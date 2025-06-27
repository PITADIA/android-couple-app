import SwiftUI
import PhotosUI
import Photos

// MARK: - Shared Image Picker
struct ImagePicker: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.dismiss) private var dismiss
    
    func makeUIViewController(context: Context) -> UIViewController {
        let coordinator = context.coordinator
        
        // Vérifier le statut des permissions photos
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        print("📸 ImagePicker: Statut permission actuel: \(coordinator.statusDescription(status))")
        
        // Si permission pas encore déterminée, la demander explicitement
        if status == .notDetermined {
            print("📸 ImagePicker: Permission non déterminée - Demande explicite")
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    print("📸 ImagePicker: Nouvelle permission accordée: \(coordinator.statusDescription(newStatus))")
                    coordinator.openPhotoPickerWithStatus(newStatus)
                }
            }
            // Retourner un contrôleur temporaire en attendant
            return coordinator.createLoadingViewController()
        }
        
        // Permission déjà déterminée, ouvrir directement le picker approprié
        return coordinator.createPhotoPickerWithStatus(status)
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePicker
        weak var currentViewController: UIViewController?
        
        init(_ parent: ImagePicker) {
            self.parent = parent
        }
        
        // MARK: - Helper Methods
        
        func statusDescription(_ status: PHAuthorizationStatus) -> String {
            switch status {
            case .notDetermined: return "Non déterminé"
            case .restricted: return "Restreint"
            case .denied: return "Refusé"
            case .authorized: return "Autorisé"
            case .limited: return "Limité"
            @unknown default: return "Inconnu"
            }
        }
        
        func createLoadingViewController() -> UIViewController {
            let loadingVC = UIViewController()
            loadingVC.view.backgroundColor = .systemBackground
            
            let activityIndicator = UIActivityIndicatorView(style: .large)
            activityIndicator.translatesAutoresizingMaskIntoConstraints = false
            activityIndicator.startAnimating()
            
            let label = UILabel()
            label.text = "Demande d'autorisation..."
            label.textAlignment = .center
            label.translatesAutoresizingMaskIntoConstraints = false
            
            loadingVC.view.addSubview(activityIndicator)
            loadingVC.view.addSubview(label)
            
            NSLayoutConstraint.activate([
                activityIndicator.centerXAnchor.constraint(equalTo: loadingVC.view.centerXAnchor),
                activityIndicator.centerYAnchor.constraint(equalTo: loadingVC.view.centerYAnchor, constant: -20),
                label.centerXAnchor.constraint(equalTo: loadingVC.view.centerXAnchor),
                label.topAnchor.constraint(equalTo: activityIndicator.bottomAnchor, constant: 16)
            ])
            
            self.currentViewController = loadingVC
            return loadingVC
        }
        
        func createPhotoPickerWithStatus(_ status: PHAuthorizationStatus) -> UIViewController {
            switch status {
            case .authorized:
                print("📸 ImagePicker: Accès complet autorisé - utilisation du picker standard")
                return createPHPickerViewController()
            case .limited:
                print("📸 ImagePicker: Accès limité détecté - utilisation de l'interface personnalisée")
                return createLimitedAccessViewController()
            case .denied, .restricted:
                return createDeniedViewController()
            case .notDetermined:
                return createLoadingViewController()
            @unknown default:
                return createDeniedViewController()
            }
        }
        
        func openPhotoPickerWithStatus(_ status: PHAuthorizationStatus) {
            guard let currentVC = currentViewController else { return }
            
            let newVC = createPhotoPickerWithStatus(status)
            
            // Remplacer le contrôleur actuel
            if let navigationController = currentVC.navigationController {
                navigationController.setViewControllers([newVC], animated: true)
            } else if let presentingVC = currentVC.presentingViewController {
                currentVC.dismiss(animated: false) {
                    presentingVC.present(newVC, animated: true)
                }
            }
        }
        
        private func createPHPickerViewController() -> UIViewController {
            if #available(iOS 14, *) {
                var configuration = PHPickerConfiguration(photoLibrary: .shared())
                configuration.filter = .images
                configuration.selectionLimit = 1
                
                let picker = PHPickerViewController(configuration: configuration)
                picker.delegate = self
                
                print("📸 ImagePicker: Utilisation de PHPickerViewController avec photoLibrary.shared()")
                return picker
            } else {
                return createUIImagePickerController()
            }
        }
        
        private func createUIImagePickerController() -> UIViewController {
            let picker = UIImagePickerController()
            picker.delegate = self
            picker.sourceType = .photoLibrary
            picker.allowsEditing = true
            
            print("📸 ImagePicker: Utilisation de UIImagePickerController")
            return picker
        }
        
        private func createLimitedAccessViewController() -> UIViewController {
            let limitedVC = LimitedAccessViewController()
            limitedVC.onImageSelected = { [weak self] image in
                print("📸 ImagePicker: Image sélectionnée via LimitedAccessViewController")
                self?.parent.image = image
                self?.parent.dismiss()
            }
            limitedVC.onCancel = { [weak self] in
                print("📸 ImagePicker: Annulation via LimitedAccessViewController")
                self?.parent.dismiss()
            }
            // Envelopper dans un NavigationController pour la barre de navigation
            let navController = UINavigationController(rootViewController: limitedVC)
            return navController
        }
        
        private func createDeniedViewController() -> UIViewController {
            let deniedVC = UIViewController()
            deniedVC.view.backgroundColor = .systemBackground
            
            let stackView = UIStackView()
            stackView.axis = .vertical
            stackView.spacing = 20
            stackView.alignment = .center
            stackView.translatesAutoresizingMaskIntoConstraints = false
            
            let iconLabel = UILabel()
            iconLabel.text = "📸"
            iconLabel.font = .systemFont(ofSize: 60)
            
            let titleLabel = UILabel()
            titleLabel.text = "Accès aux photos requis"
            titleLabel.font = .boldSystemFont(ofSize: 20)
            titleLabel.textAlignment = .center
            
            let messageLabel = UILabel()
            messageLabel.text = "Pour ajouter des photos, veuillez autoriser l'accès dans les Réglages"
            messageLabel.font = .systemFont(ofSize: 16)
            messageLabel.textAlignment = .center
            messageLabel.numberOfLines = 0
            
            let settingsButton = UIButton(type: .system)
            settingsButton.setTitle("Ouvrir les Réglages", for: .normal)
            settingsButton.titleLabel?.font = .boldSystemFont(ofSize: 16)
            settingsButton.backgroundColor = .systemBlue
            settingsButton.setTitleColor(.white, for: .normal)
            settingsButton.layer.cornerRadius = 8
            settingsButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 24, bottom: 12, right: 24)
            settingsButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
            
            let cancelButton = UIButton(type: .system)
            cancelButton.setTitle("Annuler", for: .normal)
            cancelButton.titleLabel?.font = .systemFont(ofSize: 16)
            cancelButton.addTarget(self, action: #selector(cancelSelection), for: .touchUpInside)
            
            stackView.addArrangedSubview(iconLabel)
            stackView.addArrangedSubview(titleLabel)
            stackView.addArrangedSubview(messageLabel)
            stackView.addArrangedSubview(settingsButton)
            stackView.addArrangedSubview(cancelButton)
            
            deniedVC.view.addSubview(stackView)
            
            NSLayoutConstraint.activate([
                stackView.centerXAnchor.constraint(equalTo: deniedVC.view.centerXAnchor),
                stackView.centerYAnchor.constraint(equalTo: deniedVC.view.centerYAnchor),
                stackView.leadingAnchor.constraint(greaterThanOrEqualTo: deniedVC.view.leadingAnchor, constant: 40),
                stackView.trailingAnchor.constraint(lessThanOrEqualTo: deniedVC.view.trailingAnchor, constant: -40)
            ])
            
            return deniedVC
        }
        
        @objc private func openSettings() {
            guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(settingsUrl)
        }
        
        @objc private func cancelSelection() {
            parent.dismiss()
        }
        
        // MARK: - PHPickerViewControllerDelegate (iOS 14+)
        @available(iOS 14, *)
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            print("📸 ImagePicker: PHPicker - \(results.count) image(s) sélectionnée(s)")
            
            guard let result = results.first else {
                print("📸 ImagePicker: Aucune image sélectionnée")
                parent.dismiss()
                return
            }
            
            if result.itemProvider.canLoadObject(ofClass: UIImage.self) {
                print("📸 ImagePicker: Début du chargement asynchrone de l'image")
                result.itemProvider.loadObject(ofClass: UIImage.self) { image, error in
                    DispatchQueue.main.async {
                        if let error = error {
                            print("❌ ImagePicker: Erreur chargement image: \(error)")
                            self.parent.dismiss()
                        } else if let uiImage = image as? UIImage {
                            print("✅ ImagePicker: Image chargée avec succès")
                            print("📸 ImagePicker: Assignation de l'image au parent")
                            self.parent.image = uiImage
                            print("📸 ImagePicker: Tentative de fermeture du picker")
                            self.parent.dismiss()
                            print("📸 ImagePicker: Commande de fermeture envoyée")
                        } else {
                            print("❌ ImagePicker: Image nil après chargement")
                            self.parent.dismiss()
                        }
                    }
                }
            } else {
                print("❌ ImagePicker: Impossible de charger l'image")
                parent.dismiss()
            }
        }
        
        // MARK: - UIImagePickerControllerDelegate (iOS < 14)
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            print("📸 ImagePicker: UIImagePicker - Image sélectionnée")
            
            if let editedImage = info[.editedImage] as? UIImage {
                parent.image = editedImage
                print("✅ ImagePicker: Image éditée récupérée")
            } else if let originalImage = info[.originalImage] as? UIImage {
                parent.image = originalImage
                print("✅ ImagePicker: Image originale récupérée")
            }
            
            parent.dismiss()
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            print("📸 ImagePicker: Sélection annulée")
            parent.dismiss()
        }
    }
}

// MARK: - Limited Access View Controller
class LimitedAccessViewController: UIViewController {
    var onImageSelected: ((UIImage) -> Void)?
    var onCancel: (() -> Void)?
    
    private var limitedAssets: [PHAsset] = []
    private var collectionView: UICollectionView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        loadLimitedAssets()
    }
    
    private func setupUI() {
        view.backgroundColor = .systemBackground
        title = "Photos autorisées"
        
        // Navigation bar
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .cancel,
            target: self,
            action: #selector(cancelTapped)
        )
        
        // Collection view layout
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 2
        layout.minimumLineSpacing = 2
        
        let screenWidth = UIScreen.main.bounds.width
        let itemWidth = (screenWidth - 6) / 3 // 3 colonnes avec 2px d'espacement
        layout.itemSize = CGSize(width: itemWidth, height: itemWidth)
        
        // Collection view
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .systemBackground
        collectionView.delegate = self
        collectionView.dataSource = self
        collectionView.register(PhotoCell.self, forCellWithReuseIdentifier: "PhotoCell")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        
        view.addSubview(collectionView)
        
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    private func loadLimitedAssets() {
        print("📸 LimitedAccessViewController: Chargement des assets limités")
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        fetchOptions.includeHiddenAssets = false
        
        let allPhotos = PHAsset.fetchAssets(with: .image, options: fetchOptions)
        print("📸 LimitedAccessViewController: \(allPhotos.count) photos accessibles en mode limité")
        
        limitedAssets = []
        for i in 0..<allPhotos.count {
            let asset = allPhotos.object(at: i)
            limitedAssets.append(asset)
        }
        
        DispatchQueue.main.async {
            self.collectionView.reloadData()
        }
        
        if limitedAssets.isEmpty {
            showEmptyState()
        }
    }
    
    private func showEmptyState() {
        let emptyView = UIView()
        emptyView.backgroundColor = .systemBackground
        
        let stackView = UIStackView()
        stackView.axis = .vertical
        stackView.spacing = 16
        stackView.alignment = .center
        stackView.translatesAutoresizingMaskIntoConstraints = false
        
        let iconLabel = UILabel()
        iconLabel.text = "📸"
        iconLabel.font = .systemFont(ofSize: 60)
        
        let titleLabel = UILabel()
        titleLabel.text = "Aucune photo accessible"
        titleLabel.font = .boldSystemFont(ofSize: 20)
        titleLabel.textAlignment = .center
        
        let messageLabel = UILabel()
        messageLabel.text = "Vous pouvez sélectionner plus de photos dans les Réglages"
        messageLabel.font = .systemFont(ofSize: 16)
        messageLabel.textColor = .secondaryLabel
        messageLabel.textAlignment = .center
        messageLabel.numberOfLines = 0
        
        let settingsButton = UIButton(type: .system)
        settingsButton.setTitle("Ouvrir les Réglages", for: .normal)
        settingsButton.titleLabel?.font = .boldSystemFont(ofSize: 16)
        settingsButton.backgroundColor = .systemBlue
        settingsButton.setTitleColor(.white, for: .normal)
        settingsButton.layer.cornerRadius = 8
        settingsButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 24, bottom: 12, right: 24)
        settingsButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
        
        stackView.addArrangedSubview(iconLabel)
        stackView.addArrangedSubview(titleLabel)
        stackView.addArrangedSubview(messageLabel)
        stackView.addArrangedSubview(settingsButton)
        
        emptyView.addSubview(stackView)
        
        NSLayoutConstraint.activate([
            stackView.centerXAnchor.constraint(equalTo: emptyView.centerXAnchor),
            stackView.centerYAnchor.constraint(equalTo: emptyView.centerYAnchor),
            stackView.leadingAnchor.constraint(greaterThanOrEqualTo: emptyView.leadingAnchor, constant: 40),
            stackView.trailingAnchor.constraint(lessThanOrEqualTo: emptyView.trailingAnchor, constant: -40)
        ])
        
        view.addSubview(emptyView)
        emptyView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            emptyView.topAnchor.constraint(equalTo: view.topAnchor),
            emptyView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            emptyView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            emptyView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        collectionView.isHidden = true
    }
    
    @objc private func cancelTapped() {
        onCancel?()
    }
    
    @objc private func openSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(settingsUrl)
    }
}

// MARK: - Collection View DataSource & Delegate
extension LimitedAccessViewController: UICollectionViewDataSource, UICollectionViewDelegate {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return limitedAssets.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "PhotoCell", for: indexPath) as! PhotoCell
        let asset = limitedAssets[indexPath.item]
        cell.configure(with: asset)
        return cell
    }
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let asset = limitedAssets[indexPath.item]
        loadFullSizeImage(from: asset)
    }
    
    private func loadFullSizeImage(from asset: PHAsset) {
        print("📸 LimitedAccessViewController: Chargement image haute résolution")
        
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
        ) { [weak self] image, info in
            DispatchQueue.main.async {
                if let image = image {
                    print("✅ LimitedAccessViewController: Image chargée avec succès")
                    self?.onImageSelected?(image)
                } else {
                    print("❌ LimitedAccessViewController: Échec chargement image")
                }
            }
        }
    }
}

// MARK: - Photo Cell
class PhotoCell: UICollectionViewCell {
    private let imageView = UIImageView()
    private let activityIndicator = UIActivityIndicatorView(style: .medium)
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.backgroundColor = .systemGray5
        imageView.translatesAutoresizingMaskIntoConstraints = false
        
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.hidesWhenStopped = true
        
        contentView.addSubview(imageView)
        contentView.addSubview(activityIndicator)
        
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: contentView.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            
            activityIndicator.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            activityIndicator.centerYAnchor.constraint(equalTo: contentView.centerYAnchor)
        ])
    }
    
    func configure(with asset: PHAsset) {
        imageView.image = nil
        activityIndicator.startAnimating()
        
        let manager = PHImageManager.default()
        let options = PHImageRequestOptions()
        options.isSynchronous = false
        options.deliveryMode = .opportunistic
        options.resizeMode = .exact
        options.isNetworkAccessAllowed = false
        
        let targetSize = CGSize(width: frame.width * 2, height: frame.height * 2) // 2x pour Retina
        
        manager.requestImage(
            for: asset,
            targetSize: targetSize,
            contentMode: .aspectFill,
            options: options
        ) { [weak self] image, info in
            DispatchQueue.main.async {
                self?.activityIndicator.stopAnimating()
                if let image = image {
                    self?.imageView.image = image
                } else {
                    // Image par défaut en cas d'échec
                    self?.imageView.backgroundColor = .systemGray4
                }
            }
        }
    }
}

// MARK: - Standard Image Picker (Alternative qui fonctionne)
struct StandardImagePicker: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.dismiss) private var dismiss
    
    func makeUIViewController(context: Context) -> UIViewController {
        let coordinator = context.coordinator
        
        // Vérifier le statut des permissions photos
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        print("📸 StandardImagePicker: Statut permission actuel: \(coordinator.statusDescription(status))")
        
        // Si permission pas encore déterminée, la demander explicitement
        if status == .notDetermined {
            print("📸 StandardImagePicker: Permission non déterminée - Demande explicite")
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    print("📸 StandardImagePicker: Nouvelle permission accordée: \(coordinator.statusDescription(newStatus))")
                    coordinator.openPhotoPickerWithStatus(newStatus)
                }
            }
            // Retourner un contrôleur temporaire en attendant
            return coordinator.createLoadingViewController()
        }
        
        // Permission déjà déterminée, ouvrir directement le picker approprié
        return coordinator.createPhotoPickerWithStatus(status)
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: StandardImagePicker
        weak var currentViewController: UIViewController?
        
        init(_ parent: StandardImagePicker) {
            self.parent = parent
        }
        
        func statusDescription(_ status: PHAuthorizationStatus) -> String {
            switch status {
            case .notDetermined: return "Non déterminé"
            case .restricted: return "Restreint"
            case .denied: return "Refusé"
            case .authorized: return "Autorisé"
            case .limited: return "Limité"
            @unknown default: return "Inconnu"
            }
        }
        
        func createLoadingViewController() -> UIViewController {
            let loadingVC = UIViewController()
            loadingVC.view.backgroundColor = .systemBackground
            
            let activityIndicator = UIActivityIndicatorView(style: .large)
            activityIndicator.translatesAutoresizingMaskIntoConstraints = false
            activityIndicator.startAnimating()
            
            loadingVC.view.addSubview(activityIndicator)
            NSLayoutConstraint.activate([
                activityIndicator.centerXAnchor.constraint(equalTo: loadingVC.view.centerXAnchor),
                activityIndicator.centerYAnchor.constraint(equalTo: loadingVC.view.centerYAnchor)
            ])
            
            self.currentViewController = loadingVC
            return loadingVC
        }
        
        func createPhotoPickerWithStatus(_ status: PHAuthorizationStatus) -> UIViewController {
            switch status {
            case .authorized:
                print("📸 StandardImagePicker: Accès complet autorisé - utilisation du picker standard")
                return createPHPickerViewController()
            case .limited:
                print("📸 StandardImagePicker: Accès limité détecté - utilisation de l'interface personnalisée")
                return createLimitedAccessViewController()
            case .denied, .restricted:
                return createDeniedViewController()
            case .notDetermined:
                return createLoadingViewController()
            @unknown default:
                return createDeniedViewController()
            }
        }
        
        func openPhotoPickerWithStatus(_ status: PHAuthorizationStatus) {
            guard let currentVC = currentViewController else { return }
            
            let newVC = createPhotoPickerWithStatus(status)
            
            // Remplacer le contrôleur actuel
            if let navigationController = currentVC.navigationController {
                navigationController.setViewControllers([newVC], animated: true)
            } else if let presentingVC = currentVC.presentingViewController {
                currentVC.dismiss(animated: false) {
                    presentingVC.present(newVC, animated: true)
                }
            }
        }
        
        private func createPHPickerViewController() -> UIViewController {
            var configuration = PHPickerConfiguration(photoLibrary: .shared())
            configuration.filter = .images
            configuration.selectionLimit = 1
            
            let picker = PHPickerViewController(configuration: configuration)
            picker.delegate = self
            
            print("📸 StandardImagePicker: Utilisation de PHPickerViewController")
            return picker
        }
        
        private func createLimitedAccessViewController() -> UIViewController {
            let limitedVC = LimitedAccessViewController()
            limitedVC.onImageSelected = { [weak self] image in
                print("📸 StandardImagePicker: Image sélectionnée via LimitedAccessViewController")
                self?.parent.image = image
                self?.parent.dismiss()
            }
            limitedVC.onCancel = { [weak self] in
                print("📸 StandardImagePicker: Annulation via LimitedAccessViewController")
                self?.parent.dismiss()
            }
            // Envelopper dans un NavigationController pour la barre de navigation
            let navController = UINavigationController(rootViewController: limitedVC)
            return navController
        }
        
        private func createDeniedViewController() -> UIViewController {
            let deniedVC = UIViewController()
            deniedVC.view.backgroundColor = .systemBackground
            
            let stackView = UIStackView()
            stackView.axis = .vertical
            stackView.spacing = 20
            stackView.alignment = .center
            stackView.translatesAutoresizingMaskIntoConstraints = false
            
            let iconLabel = UILabel()
            iconLabel.text = "📸"
            iconLabel.font = .systemFont(ofSize: 60)
            
            let titleLabel = UILabel()
            titleLabel.text = "Accès aux photos requis"
            titleLabel.font = .boldSystemFont(ofSize: 20)
            titleLabel.textAlignment = .center
            
            let messageLabel = UILabel()
            messageLabel.text = "Pour ajouter des photos, veuillez autoriser l'accès dans les Réglages"
            messageLabel.font = .systemFont(ofSize: 16)
            messageLabel.textAlignment = .center
            messageLabel.numberOfLines = 0
            
            let settingsButton = UIButton(type: .system)
            settingsButton.setTitle("Ouvrir les Réglages", for: .normal)
            settingsButton.titleLabel?.font = .boldSystemFont(ofSize: 16)
            settingsButton.backgroundColor = .systemBlue
            settingsButton.setTitleColor(.white, for: .normal)
            settingsButton.layer.cornerRadius = 8
            settingsButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 24, bottom: 12, right: 24)
            settingsButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
            
            let cancelButton = UIButton(type: .system)
            cancelButton.setTitle("Annuler", for: .normal)
            cancelButton.titleLabel?.font = .systemFont(ofSize: 16)
            cancelButton.addTarget(self, action: #selector(cancelSelection), for: .touchUpInside)
            
            stackView.addArrangedSubview(iconLabel)
            stackView.addArrangedSubview(titleLabel)
            stackView.addArrangedSubview(messageLabel)
            stackView.addArrangedSubview(settingsButton)
            stackView.addArrangedSubview(cancelButton)
            
            deniedVC.view.addSubview(stackView)
            
            NSLayoutConstraint.activate([
                stackView.centerXAnchor.constraint(equalTo: deniedVC.view.centerXAnchor),
                stackView.centerYAnchor.constraint(equalTo: deniedVC.view.centerYAnchor),
                stackView.leadingAnchor.constraint(greaterThanOrEqualTo: deniedVC.view.leadingAnchor, constant: 40),
                stackView.trailingAnchor.constraint(lessThanOrEqualTo: deniedVC.view.trailingAnchor, constant: -40)
            ])
            
            return deniedVC
        }
        
        @objc private func openSettings() {
            guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(settingsUrl)
        }
        
        @objc private func cancelSelection() {
            parent.dismiss()
        }
        
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            print("📸 StandardImagePicker: L'utilisateur a sélectionné \(results.count) image(s)")
            print("📸 StandardImagePicker: Picker reçu: \(picker)")
            
            // ✅ SOLUTION: Fermer le picker IMMÉDIATEMENT
            print("📸 StandardImagePicker: Tentative de fermeture du picker...")
            picker.dismiss(animated: true) {
                print("📸 StandardImagePicker: Picker fermé avec succès !")
            }
            
            if results.isEmpty {
                print("📸 StandardImagePicker: Sélection annulée par l'utilisateur")
                return
            }
            
            guard let result = results.first else {
                print("📸 StandardImagePicker: Aucun résultat trouvé")
                return
            }
            
            print("📸 StandardImagePicker: Début du traitement de l'image...")
            
            if result.itemProvider.canLoadObject(ofClass: UIImage.self) {
                print("📸 StandardImagePicker: ItemProvider peut charger UIImage")
                result.itemProvider.loadObject(ofClass: UIImage.self) { image, error in
                    print("📸 StandardImagePicker: Callback loadObject appelé")
                    DispatchQueue.main.async {
                        if let error = error {
                            print("❌ StandardImagePicker: Erreur lors du chargement: \(error.localizedDescription)")
                        } else if let uiImage = image as? UIImage {
                            print("✅ StandardImagePicker: Image chargée avec succès - Taille: \(uiImage.size)")
                            self.parent.image = uiImage
                            print("📸 StandardImagePicker: Image assignée au parent")
                        } else {
                            print("❌ StandardImagePicker: Impossible de convertir en UIImage")
                        }
                    }
                }
            } else {
                print("❌ StandardImagePicker: ItemProvider ne peut pas charger UIImage")
            }
        }
    }
} 