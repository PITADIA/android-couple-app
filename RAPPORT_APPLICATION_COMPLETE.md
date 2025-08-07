# Rapport Complet - CoupleApp

## Vue d'Ensemble

**CoupleApp** (Love2Love) est une application iOS native développée en SwiftUI qui permet aux couples de renforcer leur relation à travers des questions quotidiennes, des défis, un journal partagé et diverses fonctionnalités interactives.

### Informations Techniques Générales

- **Plateforme**: iOS (SwiftUI)
- **Langage**: Swift
- **Backend**: Firebase (Firestore, Authentication, Functions, Storage)
- **Architecture**: MVVM + Services
- **Widgets**: Support iOS complet (Home Screen + Lock Screen)
- **Localisation**: Français/Anglais avec String Catalogs (.xcstrings)

---

## 1. 🚀 SYSTÈME D'ONBOARDING

### Structure du Flow d'Onboarding

L'application propose un processus d'onboarding complet et personnalisé:

**Étapes principales** (dans `OnboardingViewModel.swift`):

1. **relationshipGoals** - Définition des objectifs du couple
2. **relationshipDate** - Date de début de relation
3. **relationshipImprovement** - Domaines d'amélioration souhaités
4. **authentication** - Authentification Apple Sign In
5. **displayName** - Nom d'affichage
6. **profilePhoto** - Photo de profil
7. **completion** - Finalisation des données
8. **loading** - Traitement des données
9. **partnerCode** - Connexion avec le partenaire
10. **categoriesPreview** - Aperçu des catégories de questions
11. **fitnessIntro** - Introduction aux fonctionnalités
12. **fitnessIntro2** - Suite de l'introduction
13. **dailyQuestionNotification** - Permission notifications
14. **subscription** - Choix d'abonnement

### Caractéristiques Techniques

- **Navigation fluide** avec barre de progression
- **Persistance des données** pendant le processus
- **Gestion des interruptions** avec récupération d'état
- **Analytics intégrés** pour chaque étape (Firebase Analytics)
- **Design responsive** avec animations

---

## 2. 🔐 AUTHENTIFICATION & FIREBASE

### Authentification

- **Apple Sign In exclusif** pour la conformité App Store
- **Gestion sécurisée des tokens** Firebase
- **Vérification des providers** d'authentification
- **Auto-reconnexion** et gestion des sessions

### Architecture Firebase

#### Firestore Collections

```
users/
├── {userId}/
│   ├── name, email, birthDate
│   ├── relationshipGoals[]
│   ├── partnerId, partnerConnectedAt
│   ├── subscription (isSubscribed, type, etc.)
│   └── ...

partnerCodes/
├── {codeId}/
│   ├── userId, isActive
│   ├── expiresAt (24h max)
│   ├── connectedPartnerId
│   └── connectedAt

dailyQuestions/
├── {questionId}/
│   ├── coupleId, questionKey, scheduledDate
│   ├── responses/ (sous-collection)
│   └── status, createdAt

journalEntries/
├── {entryId}/
│   ├── title, description, eventDate
│   ├── authorId, partnerIds[]
│   ├── imageURL, location (chiffrée)
│   └── isShared

dailyChallenges/
├── {challengeId}/
│   ├── coupleId, challengeKey, challengeDay
│   ├── userCompletions[]
│   └── scheduledDate
```

#### Firebase Functions (Cloud Functions)

**Fonctions principales** dans `firebase/functions/index.js`:

- `createPartnerCode` - Génération de codes temporaires (24h)
- `connectPartners` - Connexion sécurisée entre partenaires
- `generateDailyQuestion` - Génération automatique des questions quotidiennes
- `generateDailyChallenge` - Génération des défis quotidiens
- `notifyPartnerResponse` - Notifications push entre partenaires
- `syncJournalEntries` - Synchronisation automatique du journal

---

## 3. 📱 QUESTIONS QUOTIDIENNES

### Système de Questions

L'application propose un système sophistiqué de questions quotidiennes pour les couples:

#### Mécanisme de Progression

- **Cycle de 20 jours** par défaut
- **Questions générées automatiquement** via Firebase Functions
- **Synchronisation couple** avec identifiant `coupleId`
- **Fuseau horaire respecté** pour la génération quotidienne

#### Catégories de Questions

Définies dans `Models/QuestionDataManager.swift`:

1. **En Couple** (`en-couple`) - Questions de base (gratuit)
2. **Les Plus Hots** (`les-plus-hots`) - Questions intimes (premium)
3. **Pour Rire à Deux** (`pour-rire-a-deux`) - Questions fun (premium)
4. **Questions Profondes** (`questions-profondes`) - Réflexion (premium)
5. **À Distance** (`a-distance`) - Couples longue distance (premium)
6. **Tu Préfères** (`tu-preferes`) - Questions choix (premium)
7. **Mieux Ensemble** (`mieux-ensemble`) - Amélioration (premium)
8. **Pour un Date** (`pour-un-date`) - Idées de sorties (premium)

#### Chat Intégré

- **Messages en temps réel** avec Firestore listeners
- **Interface style MessageKit** customisée
- **Statuts de lecture** et réponses
- **Système d'attente** jusqu'à ce que les deux partenaires répondent

---

## 4. 🎯 DÉFIS QUOTIDIENS

### Fonctionnalités des Défis

Système complémentaire aux questions quotidiennes:

#### Structure des Défis

- **53 défis différents** au total
- **Progression quotidienne** synchronisée entre partenaires
- **Statuts de complétion** individuels et de couple
- **Sauvegarde des défis favoris**

#### Interface Utilisateur

- **Cartes visuelles** avec illustrations
- **Animations fluides** de progression
- **Badges de complétion** pour motivation
- **Historique des défis complétés**

---

## 5. 🤝 SYSTÈME DE CONNEXION PARTENAIRE

### Mécanisme de Codes

Système sécurisé de connexion entre partenaires:

#### Génération de Codes

- **Codes alphanumériques** de 6 caractères
- **Expiration automatique** après 24h (conformité Apple)
- **Unicité garantie** via Firebase Functions
- **Révocation possible** par l'utilisateur

#### Processus de Connexion

1. **Génération du code** par le premier partenaire
2. **Partage sécurisé** du code (hors app)
3. **Saisie et validation** par le second partenaire
4. **Connexion automatique** avec synchronisation
5. **Héritage d'abonnement** si applicable

#### Sécurité

- **Validation côté serveur** uniquement
- **Limitations temporelles** strictes
- **Audit trail** complet des connexions
- **Protection contre la réutilisation**

---

## 6. 📖 JOURNAL PARTAGÉ

### Fonctionnalités du Journal

Système de journal privé partagé entre partenaires:

#### Création d'Entrées

- **Texte libre** avec titre et description
- **Upload d'images** vers Firebase Storage
- **Géolocalisation chiffrée** pour la sécurité
- **Date/heure d'événement** personnalisable

#### Géolocalisation

Gestion avancée de la localisation dans `Models/JournalEntry.swift`:

- **Chiffrement hybride** des coordonnées sensibles
- **Métadonnées publiques** (ville, pays)
- **Service de géocodage** intégré
- **Respect de la vie privée** avec double chiffrement

#### Vues d'Affichage

- **Vue calendrier** pour navigation temporelle
- **Vue carte** avec points d'intérêt géolocalisés
- **Vue liste** chronologique classique
- **Détails d'entrée** avec photos et localisation

---

## 7. 📱 SYSTÈME DE WIDGETS iOS

### Types de Widgets Supportés

L'application propose plusieurs widgets iOS natifs:

#### Widgets Home Screen

1. **Widget Principal** (`Love2LoveWidget`)

   - Petit (systemSmall): Compteur de jours ensemble
   - Moyen (systemMedium): Statistiques complètes (premium)

2. **Widget Distance** (`Love2LoveDistanceWidget`)
   - Petit uniquement: Distance temps réel entre partenaires (premium)

#### Widgets Lock Screen

1. **Widget Circulaire** (`accessoryCircular`)

   - Compteur de jours compact

2. **Widget Carte** (`Love2LoveMapWidget`)
   - Rectangulaire: Carte avec distance (premium)

### Système de Données Widget

- **UserDefaults partagés** via App Groups
- **Mise à jour automatique** toutes les minutes
- **Gestion premium** avec blocage des fonctionnalités payantes
- **Cache optimisé** pour performance

---

## 8. 🌍 LOCALISATION

### Support Multilingue

Système de localisation moderne avec String Catalogs:

#### Langues Supportées

- **Français** (langue principale)
- **Anglais** (langue internationale)

#### Technologies Utilisées

- **String Catalogs (.xcstrings)** - Nouveau système iOS
- **NSLocalizedString** avec fallbacks
- **Détection automatique** de la langue système
- **Conversion d'unités** (km/miles) selon la région

#### Organisation des Traductions

Fichiers de localisation spécialisés:

- `UI.xcstrings` - Interface utilisateur générale
- `Categories.xcstrings` - Noms des catégories
- `DailyQuestions.xcstrings` - Textes du système de questions
- `EnCouple.xcstrings`, `LesPlus Hots.xcstrings`, etc. - Questions par catégorie

---

## 9. 💰 MODÈLE FREEMIUM

### Structure d'Abonnement

Modèle économique basé sur un freemium avec limitations:

#### Plans d'Abonnement

Définis dans `Models/User.swift`:

- **Hebdomadaire** (`com.lyes.love2love.subscription.weekly`)
- **Mensuel** (`com.lyes.love2love.subscription.monthly`)
- **Essai gratuit** de 3 jours pour tous les plans

#### Limitations Freemium

Gérées par `FreemiumManager.swift`:

**Questions Quotidiennes:**

- Gratuit: 3 premiers jours
- Premium: Accès illimité

**Catégories:**

- Gratuit: "En Couple" (64 premières questions)
- Premium: Toutes les catégories

**Défis Quotidiens:**

- Gratuit: 3 premiers jours
- Premium: Accès illimité

**Journal:**

- Gratuit: 5 entrées maximum
- Premium: Entrées illimitées

**Widgets:**

- Gratuit: Widget petit et circulaire
- Premium: Tous les widgets (moyen, distance, carte)

#### Partage d'Abonnement

- **Héritage automatique** lors de la connexion partenaire
- **Synchronisation temps réel** du statut d'abonnement
- **Logging complet** pour conformité Apple

---

## 10. 💬 SYSTÈME DE CHAT

### Chat Temps Réel

Interface de chat intégrée pour les questions quotidiennes:

#### Architecture Technique

- **Firestore listeners** pour synchronisation temps réel
- **Sous-collections** pour optimiser les performances
- **Messages typés** avec métadonnées utilisateur
- **Gestion des états** (envoyé, lu, etc.)

#### Interface Utilisateur

Style moderne inspiré des apps de messagerie:

- **Bulles de messages** différenciées par utilisateur
- **Timestamps** et statuts de lecture
- **Animations fluides** d'apparition
- **Auto-scroll** vers les nouveaux messages

#### Logique de Déblocage

- **Attente mutuelle** jusqu'à ce que les deux partenaires répondent
- **Déblocage automatique** après 24h
- **Messages d'encouragement** pour inciter à répondre

---

## 11. 🖼️ GESTION DES IMAGES

### Système de Cache

Architecture de cache optimisée dans `ImageCacheService.swift`:

#### Cache Multi-Niveaux

1. **Cache mémoire** (NSCache) - 50 images max, 100MB
2. **Cache disque** (App Group) - Partage avec widgets
3. **Cache Firebase** - Via Cloud Functions sécurisées

#### Optimisations

- **Chargement asynchrone** sans blocage UI
- **Redimensionnement automatique** pour widgets
- **Partage App Group** pour les widgets
- **Nettoyage automatique** des caches anciens

#### Sécurité

- **Accès sécurisé** aux images Firebase Storage
- **Cloud Functions** pour contourner les règles de sécurité
- **Validation côté serveur** des permissions
- **URLs signées** temporaires pour les téléchargements

---

## 12. 🔔 NOTIFICATIONS

### Types de Notifications

Système de notifications push intégré:

#### Notifications Quotidiennes

- **Questions du jour** disponibles
- **Défis quotidiens** nouveaux
- **Rappels** si pas de réponse

#### Notifications Partenaire

- **Réponse partenaire** à une question
- **Nouveau défi complété** par le partenaire
- **Nouvelle entrée journal** partagée
- **Connexion partenaire** réussie

#### Gestion FCM

- **Firebase Cloud Messaging** pour delivery
- **Tokens** synchronisés entre appareils
- **Personnalisation** selon les préférences utilisateur

---

## 13. 🛠️ SERVICES TECHNIQUES

### Services Principaux

Architecture modulaire avec services spécialisés:

#### Services Core

- `FirebaseService` - Interface Firebase principale
- `AuthenticationService` - Gestion auth Apple Sign In
- `LocalizationService` - Gestion des traductions

#### Services Fonctionnels

- `DailyQuestionService` - Gestion questions quotidiennes
- `DailyChallengeService` - Gestion défis quotidiens
- `JournalService` - CRUD operations journal
- `PartnerCodeService` - Connexions partenaires

#### Services Support

- `ImageCacheService` - Cache et optimisation images
- `LocationService` - Géolocalisation et chiffrement
- `WidgetService` - Synchronisation données widgets
- `SubscriptionService` - Gestion abonnements StoreKit

### Gestion des Erreurs

- **Logging centralisé** avec niveaux de priorité
- **Gestion gracieuse** des erreurs réseau
- **Fallbacks** pour les fonctionnalités critiques
- **Messages utilisateur** localisés

---

## 14. 🎨 DESIGN & UX

### Système de Design

Interface moderne et intuitive:

#### Palette de Couleurs

- **Rose/Rouge** comme couleur principale d'accent
- **Gris clair** (`Color(red: 0.97, green: 0.97, blue: 0.98)`) pour les fonds
- **Dégradés** pour les éléments premium
- **Mode clair** uniquement (`.preferredColorScheme(.light)`)

#### Composants Réutilisables

Dans `Views/Components/`:

- `CategoryCardView` - Cartes de catégories
- `ProgressBar` - Barres de progression animées
- `AsyncImageView` - Images avec chargement asynchrone
- `DaysTogetherCard` - Compteur de jours ensemble
- `PartnerStatusCard` - Statut de connexion partenaire

#### Animations

- **Transitions fluides** entre vues
- **Animations de chargement** avec indicateurs
- **Micro-interactions** pour feedback utilisateur
- **Animations de progression** pour gamification

---

## 15. 📊 ANALYTICS & PERFORMANCE

### Suivi Utilisateur

Intégration Firebase Analytics complète:

#### Événements Trackés

- **Progression onboarding** par étape
- **Utilisation des questions** par catégorie
- **Taux de réponse** aux questions quotidiennes
- **Engagement défis** quotidiens
- **Conversions abonnement** par source

#### Métriques Performance

- **Temps de chargement** des questions
- **Taux d'échec** des synchronisations
- **Utilisation cache** et optimisations
- **Erreurs** et crashes tracking

### Optimisations

- **Cache Realm** pour réduire les appels Firebase
- **Lazy loading** des images et données
- **Pagination** pour les gros datasets
- **Debouncing** des requêtes utilisateur

---

## 16. 🔒 SÉCURITÉ & CONFORMITÉ

### Sécurité des Données

Mesures de sécurité renforcées:

#### Chiffrement

- **Géolocalisation chiffrée** dans le journal
- **Communications sécurisées** Firebase
- **Tokens** d'authentification protégés
- **Données sensibles** jamais en clair

#### Conformité Apple

- **Apple Sign In exclusif** requis
- **Codes partenaire temporaires** (24h max)
- **Audit trail** des connexions
- **Partage d'abonnement** conforme App Store Review Guidelines

#### RGPD & Vie Privée

- **Consentement explicite** pour géolocalisation
- **Suppression de compte** complète
- **Données minimales** collectées
- **Transparence** sur l'utilisation des données

---

## 17. 🏗️ ARCHITECTURE TECHNIQUE

### Structure du Projet

Organisation modulaire claire:

```
CoupleApp/
├── App/                    # Point d'entrée app
├── Models/                 # Modèles de données
├── ViewModels/            # Logic métier (MVVM)
├── Views/                 # Interface utilisateur
│   ├── Authentication/
│   ├── Onboarding/
│   ├── Main/
│   ├── DailyQuestion/
│   ├── DailyChallenge/
│   ├── Journal/
│   ├── Components/
│   └── Settings/
├── Services/              # Services techniques
├── Utils/                 # Extensions et utilitaires
├── Love2LoveWidget/       # Extension widgets
└── firebase/              # Cloud Functions
```

### Patterns Architecturaux

- **MVVM** (Model-View-ViewModel) principal
- **Services Pattern** pour logique métier
- **Observer Pattern** avec Combine
- **Repository Pattern** pour accès données

---

## 18. 🚀 DÉPLOIEMENT & CI/CD

### Configuration Build

- **Xcode Project** avec workspace
- **App Groups** pour partage widgets
- **Entitlements** pour capabilities
- **Firebase** configuration intégrée

### Environnements

- **Développement** - Debug builds
- **Production** - App Store releases
- **Firebase** - Environment unique

---

## 19. 📈 MÉTRIQUES & KPIs

### Métriques Clés

Indicateurs de succès de l'application:

#### Engagement

- **Taux de rétention** J1, J7, J30
- **Questions quotidiennes** répondues
- **Défis complétés** par couple
- **Entrées journal** créées par semaine

#### Conversion

- **Taux de conversion** freemium → premium
- **Durée moyenne** avant abonnement
- **Taux de churn** abonnements
- **LTV** (Lifetime Value) par utilisateur

#### Technique

- **Temps de chargement** moyen
- **Taux d'erreur** synchronisation
- **Utilisation cache** efficacité
- **Crashes** et bugs reportés

---

## 20. 🔮 ÉVOLUTIONS FUTURES

### Fonctionnalités Potentielles

Pistes d'amélioration identifiées:

#### Nouvelles Fonctionnalités

- **Vidéos couples** intégrées
- **Challenges photo** quotidiens
- **Calendrier événements** partagé
- **Stats relation** avancées

#### Améliorations Techniques

- **Mode offline** amélioré
- **Synchronisation** multi-appareils
- **Apple Watch** companion app
- **iPad** optimisation

#### Expansion

- **Nouvelles langues** (espagnol, italien)
- **Contenu** généré par IA
- **Communauté** couples
- **Coaching** relationnel intégré

---

## 📋 RÉSUMÉ EXÉCUTIF

**CoupleApp** est une application iOS sophistiquée et bien architecturée qui offre une expérience complète pour les couples souhaitant renforcer leur relation. L'application se distingue par:

### Points Forts

✅ **Architecture technique solide** avec Firebase et SwiftUI
✅ **Expérience utilisateur** fluide et intuitive  
✅ **Modèle économique** freemium bien structuré
✅ **Sécurité** et conformité Apple respectées
✅ **Fonctionnalités riches** (questions, défis, journal, widgets)
✅ **Localisation** professionnelle français/anglais
✅ **Performance** optimisée avec cache multi-niveaux

### Défis Techniques Relevés

- **Synchronisation temps réel** entre partenaires
- **Gestion complexe** des états freemium
- **Widgets iOS** avancés avec limitations premium
- **Géolocalisation chiffrée** pour le journal
- **Système de codes** partenaire sécurisé

### Impact Utilisateur

L'application répond à un besoin réel de connexion et communication dans les couples modernes, avec une approche gamifiée et progressive qui encourage l'engagement à long terme.

---
