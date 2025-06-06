# CoupleApp

Une application iOS native développée en SwiftUI pour améliorer les relations de couple à travers des questions profondes et des conversations significatives.

## 🎯 Fonctionnalités

- **Onboarding complet** : Processus de création de profil en plusieurs étapes
- **Authentification Apple** : Connexion sécurisée avec Sign in with Apple
- **Abonnement premium** : 3 jours d'essai gratuit puis 4,99€/semaine
- **Design moderne** : Interface utilisateur inspirée des meilleures applications de dating

## 📱 Flux de l'application

1. **Écran d'accueil** : Présentation de l'app avec statistiques
2. **Onboarding** :
   - Saisie du prénom
   - Sélection de la date de naissance
   - Choix des objectifs de relation
   - Durée de la relation actuelle
   - Code partenaire (optionnel)
   - Écran de chargement avec animation
   - Page d'abonnement premium
   - Authentification Apple
3. **Application principale** : Page "Hello World" après connexion

## 🏗️ Architecture

```
CoupleApp/
├── App/                    # Point d'entrée de l'application
│   ├── CoupleApp.swift    # App principale
│   ├── AppDelegate.swift  # Délégué d'application
│   └── ContentView.swift  # Vue racine
├── Models/                # Modèles de données
│   ├── AppState.swift     # État global de l'app
│   └── User.swift         # Modèle utilisateur
├── ViewModels/            # Logique métier
│   └── OnboardingViewModel.swift
├── Views/                 # Vues SwiftUI
│   ├── Authentication/    # Vues d'authentification
│   ├── Onboarding/       # Vues d'onboarding
│   └── Main/             # Vues principales
└── Services/             # Services
    └── SubscriptionService.swift
```

## 🛠️ Configuration

### Prérequis

- Xcode 15.0+
- iOS 17.0+
- Compte développeur Apple (pour Sign in with Apple)

### Installation

1. Ouvrir le projet dans Xcode
2. Configurer votre Team ID dans les paramètres du projet
3. Modifier le Bundle Identifier si nécessaire
4. Activer "Sign in with Apple" dans les Capabilities
5. Configurer StoreKit pour les achats in-app

### Configuration StoreKit

Le fichier `CoupleApp.storekit` contient la configuration pour l'abonnement :

- **Produit** : `com.coupleapp.subscription.weekly`
- **Prix** : 4,99€ par semaine
- **Essai gratuit** : 3 jours

## 🎨 Design

L'application reprend le design de votre application NutritionIA avec :

- **Couleurs** : Dégradé rouge/orange pour les fonds
- **Typographie** : Titres en gras, textes clairs
- **Icônes** : Émojis de flamme 🔥 comme élément central
- **Animations** : Transitions fluides entre les étapes

## 🔧 Personnalisation

### Modifier les questions d'onboarding

Éditez le fichier `OnboardingViewModel.swift` :

```swift
let relationshipGoals = [
    "👫 Mieux connaître mon partenaire",
    "🔥 Aborder des sujets délicats",
    "🌶️ Pimenter notre relation",
    "🎉 S'amuser ensemble"
]
```

### Changer le prix de l'abonnement

1. Modifier le fichier `CoupleApp.storekit`
2. Mettre à jour le texte dans `SubscriptionStepView.swift`
3. Configurer les produits dans App Store Connect

## 🚀 Déploiement

1. **Tests** : Utiliser le simulateur iOS avec StoreKit Testing
2. **TestFlight** : Uploader via Xcode pour les tests bêta
3. **App Store** : Soumettre pour review après configuration complète

## 📝 Notes importantes

- L'authentification Apple est configurée mais nécessite un certificat valide
- Les achats in-app sont en mode test (simulation)
- Le code partenaire est généré aléatoirement pour la démo
- La page principale affiche "Hello World" comme demandé

## 🔐 Sécurité

- Toutes les données utilisateur sont stockées localement
- Authentification sécurisée via Apple
- Pas de collecte de données personnelles sans consentement

## 📞 Support

Pour toute question ou problème, consultez la documentation Apple pour :

- SwiftUI
- StoreKit 2
- Sign in with Apple
