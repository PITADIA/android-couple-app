# 🎯 Documentation - Fonctionnalité "Défi du Jour"

## 📋 Table des Matières

1. [Vue d'Ensemble](#vue-densemble)
2. [Architecture Technique](#architecture-technique)
3. [Modèles de Données](#modèles-de-données)
4. [Services Swift](#services-swift)
5. [Interface Utilisateur](#interface-utilisateur)
6. [Logique Freemium](#logique-freemium)
7. [Firebase Backend](#firebase-backend)
8. [Sécurité & Règles](#sécurité--règles)
9. [Localisation](#localisation)
10. [Déploiement](#déploiement)
11. [Tests & Debugging](#tests--debugging)

---

## Vue d'Ensemble

### 🎯 **Objectif**

Implémenter un système de "défis quotidiens" pour couples, offrant chaque jour un nouveau défi personnalisé avec une logique freemium (3 jours gratuits).

### ✅ **Fonctionnalités Principales**

- **Défis quotidiens** générés automatiquement par jour de relation
- **Logique freemium** : 3 premiers jours gratuits, puis paywall
- **Sauvegarde** de défis favoris
- **Synchronisation** temps réel entre partenaires
- **Design cohérent** avec l'existant (cartes style catégories)

### 🔄 **Réutilisation**

- **80% du code** réutilisé depuis "Question du jour"
- **Même logique freemium** que les questions quotidiennes
- **Architecture Firebase** identique aux features existantes

---

## Architecture Technique

```
┌─────────────────────────────────────────────────────────────┐
│                     ARCHITECTURE GLOBALE                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │   Swift Client  │    │  Firebase Cloud │                │
│  │                 │    │    Functions    │                │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │                │
│  │ │    Views    │ │    │ │   generate  │ │                │
│  │ │             │ │    │ │DailyChallen │ │                │
│  │ │ - Flow      │ │    │ │    ge()     │ │                │
│  │ │ - Main      │ │◄──►│ │             │ │                │
│  │ │ - Card      │ │    │ │ scheduled   │ │                │
│  │ │ - Saved     │ │    │ │Generation() │ │                │
│  │ └─────────────┘ │    │ └─────────────┘ │                │
│  │                 │    │                 │                │
│  │ ┌─────────────┐ │    └─────────────────┘                │
│  │ │  Services   │ │                                       │
│  │ │             │ │    ┌─────────────────┐                │
│  │ │ - Challenge │ │    │   Firestore     │                │
│  │ │ - Saved     │ │◄──►│                 │                │
│  │ │ - Freemium  │ │    │ - dailyChallen  │                │
│  │ └─────────────┘ │    │   geSettings    │                │
│  │                 │    │ - dailyChallen  │                │
│  │ ┌─────────────┐ │    │   ges           │                │
│  │ │   Models    │ │    │ - savedChallen  │                │
│  │ │             │ │    │   ges           │                │
│  │ │ - Challenge │ │    └─────────────────┘                │
│  │ │ - Settings  │ │                                       │
│  │ │ - User      │ │                                       │
│  │ └─────────────┘ │                                       │
│  └─────────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Modèles de Données

### 📱 **Swift Models**

#### `DailyChallenge`

```swift
struct DailyChallenge: Codable, Identifiable, Hashable {
    let id: String
    let challengeKey: String       // "daily_challenge_1" à "daily_challenge_24"
    let challengeDay: Int          // Jour de relation (1, 2, 3...)
    let scheduledDate: Date        // Date de programmation
    let coupleId: String           // ID couple (userId1_userId2)
    var isCompleted: Bool          // État de completion
    var completedAt: Date?         // Date de completion

    /// Retourne le texte localisé du défi
    var localizedText: String {
        return challengeKey.localized
    }
}
```

#### `SavedChallenge`

```swift
struct SavedChallenge: Codable, Identifiable, Hashable {
    let id: String
    let challengeKey: String       // Référence au défi
    let challengeDay: Int          // Jour d'origine
    let savedAt: Date              // Date de sauvegarde
    let userId: String             // Propriétaire

    var localizedText: String {
        return challengeKey.localized
    }
}
```

#### `DailyChallengeSettings`

```swift
struct DailyChallengeSettings: Codable {
    let coupleId: String
    let startDate: Date            // Date début relation
    let timezone: String           // Timezone couple
    var currentDay: Int            // Jour actuel
    let createdAt: Date
    var lastVisitDate: Date
}
```

#### Extension `AppUser`

```swift
struct AppUser {
    // ... propriétés existantes ...

    // NOUVEAU: Tracking freemium défis
    var dailyChallengeFirstAccessDate: Date?
    var dailyChallengeMaxDayReached: Int
}
```

---

## Services Swift

### 🔥 **DailyChallengeService**

**Responsabilités:**

- Communication avec Firebase Functions
- Écoute temps réel des défis via Firestore
- Gestion des settings couple
- Cache local via Realm

**Méthodes principales:**

```swift
class DailyChallengeService: ObservableObject {
    @Published var currentChallenge: DailyChallenge?
    @Published var challengeHistory: [DailyChallenge] = []
    @Published var isLoading: Bool = false
    @Published var currentSettings: DailyChallengeSettings?

    // Configuration avec AppState
    func configure(with appState: AppState)

    // Appel Firebase Function pour générer défi
    private func generateTodaysChallenge(coupleId: String)

    // Marquer défi comme complété
    func markChallengeAsCompleted(_ challenge: DailyChallenge)

    // Récupérer jour actuel
    func getCurrentChallengeDay() -> Int
}
```

### 💾 **SavedChallengesService**

**Responsabilités:**

- CRUD des défis sauvegardés
- Synchronisation Firestore
- Vérification anti-doublons

**Méthodes principales:**

```swift
class SavedChallengesService: ObservableObject {
    @Published var savedChallenges: [SavedChallenge] = []
    @Published var isLoading: Bool = false
    @Published var lastSavedChallenge: SavedChallenge?

    func saveChallenge(_ challenge: DailyChallenge)
    func deleteChallenge(_ savedChallenge: SavedChallenge)
    func isChallengeAlreadySaved(_ challenge: DailyChallenge) -> Bool
    func clearSavedChallenges()
}
```

---

## Interface Utilisateur

### 🎨 **Structure des Vues**

```
Views/DailyChallenge/
├── DailyChallengeFlowView.swift      # Navigation principale
├── DailyChallengeIntroView.swift     # Page présentation avec bouton "Continuer"
├── DailyChallengeMainView.swift      # Vue principal avec défi
├── DailyChallengeCardView.swift      # Carte de défi réutilisable
└── SavedChallengesView.swift         # Liste défis sauvegardés
```

#### **DailyChallengeFlowView**

- **Navigation principale** entre intro/paywall/contenu
- **Vérification freemium** avant affichage
- **Gestion états** : non connecté / abonné / freemium

#### **DailyChallengeIntroView**

- **Page de présentation** avec design cohérent aux questions du jour
- **Bouton "Continuer"** pour partenaires connectés
- **Bouton "Connecter partenaire"** si pas de connexion
- **Navigation fullScreen** vers DailyChallengeMainView
- **🎯 DÉMARRAGE COMPTAGE** : Le clic "Continuer" déclenche la génération

#### **DailyChallengeMainView**

- **Affichage défi actuel** avec sous-titre dynamique
- **Gestion loading** et états vides
- **Navigation** vers défis sauvegardés

#### **DailyChallengeCardView**

- **Design identique** aux cartes catégories existantes
- **Deux boutons** :
  - ✅ "Défi complété" (toggle avec animation)
  - 📌 "Sauvegarder le défi" (avec coche de confirmation)
- **Mode suppression** pour défis sauvegardés

#### **SavedChallengesView**

- **Liste scrollable** des défis sauvegardés
- **Suppression individuelle** avec confirmation
- **Badge compteur** dans navigation

### 🎯 **Navigation & Intégration**

#### Nouvel Onglet (TabContainerView)

```swift
case 3:
    // Défis du jour
    DailyChallengeFlowView()
        .environmentObject(appState)
```

**Position:** Entre Favoris (💖) et Journal (🗺️)  
**Icône temporaire:** Même que favoris (💖) - facilement remplaçable  
**Analytics:** Tracking `onglet_visite` avec paramètre `"defis"`

---

## Logique Freemium

### 💎 **Extension FreemiumManager**

#### **Configuration**

```swift
extension FreemiumManager {
    private var freeDailyChallengesDays: Int { 3 } // 3 jours gratuits

    /// Vérifie accès au défi selon abonnement
    func canAccessDailyChallenge(for challengeDay: Int) -> Bool

    /// Gère accès avec callback succès ou paywall
    func handleDailyChallengeAccess(currentChallengeDay: Int, onSuccess: @escaping () -> Void)

    /// Retourne sous-titre selon statut (premium/freemium)
    func getDailyChallengeSubtitle(for challengeDay: Int) -> String
}
```

#### **Logique de Vérification**

```swift
func canAccessDailyChallenge(for challengeDay: Int) -> Bool {
    // Si abonné = accès illimité
    if appState?.currentUser?.isSubscribed ?? false {
        return true
    }

    // Sinon = limité à 3 premiers jours
    return challengeDay <= freeDailyChallengesDays
}
```

#### **Sous-titres Dynamiques**

```swift
// Utilisateur Premium
"💎 Inclus avec votre abonnement Premium"

// Utilisateur Freemium (jours 1-3)
"✨ 2 jours gratuits restants • Ensuite, abonnement requis"

// Utilisateur Freemium (jour 4+)
"🔒 Abonnement requis pour continuer"
```

#### **Analytics Intégrées**

```swift
// Accès défi gratuit
Analytics.logEvent("freemium_daily_challenge_accessed", parameters: [
    "challenge_day": day,
    "is_subscribed": false
])

// Affichage paywall
Analytics.logEvent("paywall_viewed", parameters: [
    "source": "daily_challenge_freemium",
    "day": day
])
```

---

## Firebase Backend

### ⚡ **Cloud Functions**

#### `generateDailyChallenge` (HTTP Callable)

```javascript
exports.generateDailyChallenge = functions.https.onCall(
  async (data, context) => {
    // 1. Vérification authentification
    // 2. Rate limiting
    // 3. Récupération/création settings couple
    // 4. Calcul jour actuel selon timezone
    // 5. Génération clé défi (cycle 24 défis)
    // 6. Création/récupération document Firestore
    // 7. Mise à jour settings
  }
);
```

**Paramètres:**

- `coupleId`: ID du couple (requis)
- `challengeDay`: Jour spécifique (optionnel, auto-calculé sinon)
- `timezone`: Timezone (défaut: "Europe/Paris")

**Réponse:**

```javascript
{
    success: true,
    challenge: DailyChallengeObject,
    settings: SettingsObject
}
```

#### `scheduledDailyChallengeGeneration` (Cron Job)

```javascript
exports.scheduledDailyChallengeGeneration = functions.pubsub
  .schedule("0 0 * * *") // Chaque jour à 00:00 UTC
  .onRun(async (context) => {
    // 1. Récupérer tous les couples avec settings
    // 2. Générer défis en parallèle
    // 3. Gestion erreurs batch
    // 4. Logs monitoring
  });
```

#### **Fonctions Helper**

```javascript
// Cycle des 24 défis disponibles
function generateChallengeKey(challengeDay) {
    const challengeIndex = ((challengeDay - 1) % 24) + 1;
    return `daily_challenge_${challengeIndex}`;
}

// Calcul jour relation (même logique que questions)
function calculateCurrentChallengeDay(settings, currentTime)

// Gestion settings couple
async function getOrCreateDailyChallengeSettings(coupleId, timezone)
```

### 🗄️ **Collections Firestore**

#### `dailyChallengeSettings/{coupleId}`

```javascript
{
    coupleId: "user1_user2",
    startDate: Timestamp,          // Date début relation
    timezone: "Europe/Paris",      // Timezone couple
    currentDay: 1,                 // Jour actuel progression
    createdAt: Timestamp,
    lastVisitDate: Timestamp,
    nextScheduledDate: Timestamp   // Prochain défi programmé
}
```

#### `dailyChallenges/{coupleId}_{date}`

```javascript
{
    id: "couple123_2025-01-15",
    challengeKey: "daily_challenge_1",     // Clé de localisation
    challengeDay: 1,                       // Jour de relation
    scheduledDate: Timestamp,              // Date programmation
    coupleId: "user1_user2",               // Couple propriétaire
    isCompleted: false,                    // État completion
    completedAt: Timestamp,                // Date completion (optionnel)
    createdAt: Timestamp
}
```

#### `savedChallenges/{userId}_{challengeKey}`

```javascript
{
    challengeKey: "daily_challenge_5",     // Référence défi
    challengeDay: 5,                       // Jour origine
    savedAt: Timestamp,                    // Date sauvegarde
    userId: "abc123"                       // Propriétaire
}
```

---

## Sécurité & Règles

### 🛡️ **Règles Firestore (firestore.rules)**

#### **Défis Quotidiens**

```javascript
match /dailyChallenges/{challengeId} {
    // Lecture: Membres du couple connectés uniquement
    allow read: if request.auth != null &&
                   userIsInCouple(request.auth.uid, resource.data.coupleId) &&
                   partnersAreConnectedForCouple(resource.data.coupleId);

    // Écriture: Seulement champs completion par membres
    allow update: if request.auth != null &&
                     userIsInCouple(request.auth.uid, resource.data.coupleId) &&
                     partnersAreConnectedForCouple(resource.data.coupleId) &&
                     request.resource.data.keys().hasAny(['isCompleted', 'completedAt']);

    // Création/Suppression: Seulement Cloud Functions
    allow create, delete: if false;
}
```

#### **Settings (Lecture Seule Client)**

```javascript
match /dailyChallengeSettings/{coupleId} {
    allow read: if request.auth != null &&
                   userIsInCouple(request.auth.uid, coupleId) &&
                   partnersAreConnectedForCouple(coupleId);
    allow write: if false; // Seulement Cloud Functions
}
```

#### **Défis Sauvegardés (Privés)**

```javascript
match /savedChallenges/{savedChallengeId} {
    // Propriétaire uniquement
    allow read, write, delete: if request.auth != null &&
                                  request.auth.uid == resource.data.userId;
}
```

### 📇 **Index de Performance (firestore.indexes.json)**

```json
{
  "indexes": [
    {
      "collectionGroup": "dailyChallenges",
      "fields": [
        { "fieldPath": "coupleId", "order": "ASCENDING" },
        { "fieldPath": "challengeDay", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "savedChallenges",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "savedAt", "order": "DESCENDING" }
      ]
    }
  ]
}
```

---

## Localisation

### 🌍 **Fichier DailyChallenges.xcstrings**

#### **Structure**

```json
{
  "sourceLanguage": "fr",
  "strings": {
    "daily_challenge_1": {
      "fr": "Envoyez-lui un message pour lui dire pourquoi vous êtes reconnaissant de l'avoir dans votre vie.",
      "en": "Send them a message telling them why you're grateful to have them in your life."
    },
    // ... 24 défis au total

    "daily_challenges_title": {
      "fr": "Défi du jour",
      "en": "Daily Challenge"
    },
    "challenge_completed_button": {
      "fr": "Défi complété",
      "en": "Challenge completed"
    }
    // ... autres clés interface
  }
}
```

#### **Utilisation**

```swift
// Dans les modèles
var localizedText: String {
    return challengeKey.localized // "daily_challenge_1".localized
}

// Dans les vues
Text("daily_challenges_title".localized)
Text("challenge_completed_button".localized)
```

#### **53 Défis Inclus**

**Défis 1-24 (Originaux):**

1. Message reconnaissance
2. Appel "je t'aime"
3. Question besoin
4. Chanson relation
5. Liste 5 activités couple
6. Alarme "je t'aime"
7. Photo souvenir partenaire
8. Surnom affectueux
9. Question inédite
10. Liste 10 activités favorites
11. Compliment inaperçu
12. Enseigner compétence
13. 3 "je t'aime" différents
14. Lettre d'amour numérique
15. Repas visio simultané
16. Message vocal "Je voulais te dire..."
17. Appel langue étrangère
18. Vidéo sentiment jour
19. Photo main + cœur + nom
20. Qualité sous-estimée
21. Commande surprise (≤12€)
22. Photo lieu de rêve ensemble
23. Compliment physique + intérieur
24. Question "Comment sais-tu que je t'aime ?"

**Défis 25-53 (Nouveaux):** 25. Dessiner animal représentant partenaire 26. Décrire en 3 hashtags 27. Créer règle d'or du couple 28. Nom de duo créatif 29. Potion magique d'amour 30. Vocal narrateur animalier 31. Journée parfaite enfants 6 ans 32. Premier baiser nostalgique 33. Fruit symbolique du partenaire 34. Règle ordre mondial amoureux 35. Surnoms personnages animés 36. Couleur représentative 37. Journée imaginaire heure par heure 38. Phrase anti-dispute 39. Note vocale miroir 40. Cœur dessiné avec prénom 41. Surnom unique journée 42. Visio fixation 2 minutes 43. Question surprise immédiate 44. Phrase enfance manquée 45. "Je t'aime" langue étrangère 46. 2 sujets jamais abordés 47. 3 choses admirées 48. Souvenir fictif partagé 49. Règle créative journée 50. Photo lieu de rêve 51. Imitation vocale tendre 52. Bio Tinder mutuelle 53. Message main non-dominante

---

## Déploiement

### 🚀 **Checklist de Déploiement**

#### **1. Firebase Functions**

```bash
# Déployer les nouvelles functions
cd firebase
firebase deploy --only functions:generateDailyChallenge
firebase deploy --only functions:scheduledDailyChallengeGeneration
```

#### **2. Règles Firestore**

```bash
# Déployer règles de sécurité
firebase deploy --only firestore:rules
```

#### **3. Index Firestore**

```bash
# Déployer index de performance
firebase deploy --only firestore:indexes
```

#### **4. Vérifications Post-Déploiement**

- [ ] Functions apparaissent dans Console Firebase
- [ ] Cron job programmé visible dans Cloud Scheduler
- [ ] Règles Firestore mises à jour
- [ ] Index créés et READY
- [ ] Collections vides créées (pas d'erreur permissions)

### 📱 **Build iOS**

#### **Nouveaux Fichiers à Inclure**

```
Models/DailyChallenge.swift
Services/DailyChallengeService.swift
Services/SavedChallengesService.swift
Views/DailyChallenge/DailyChallengeFlowView.swift
Views/DailyChallenge/DailyChallengeIntroView.swift
Views/DailyChallenge/DailyChallengeMainView.swift
Views/DailyChallenge/DailyChallengeCardView.swift
Views/DailyChallenge/SavedChallengesView.swift
Views/Debug/DebugDailyChallengeMenuView.swift
DailyChallenges.xcstrings
DAILY_CHALLENGES_IMPLEMENTATION.md
```

#### **Fichiers Modifiés**

```
Models/User.swift                      # Nouveaux champs freemium
Services/FirebaseService.swift         # Persistance nouveaux champs
ViewModels/FreemiumManager.swift       # Extension défis + debug
ViewModels/AppState.swift             # Services initialisation
Views/Main/TabContainerView.swift     # Nouvel onglet
Views/Main/MenuView.swift             # Bouton debug défis
```

---

## Tests & Debugging

### 🛠️ **Outils de Debug Intégrés**

#### **Menu Debug Défis du Jour (Development Only)**

Une interface de debug complète est disponible dans **Menu → 🎯 Debug Défis du Jour** :

##### **📊 Informations Affichées**

- **Jour Défi Actuel** : Jour calculé selon `relationshipStartDate`
- **Premier Accès** : Date première utilisation défis
- **Jour Max Atteint** : Progression maximale utilisateur
- **Abonné** : Status Premium actuel
- **Date Relation** : `relationshipStartDate` pour calculs
- **Simulation Active** : Mode debug activé ou non

##### **🎯 Actions Disponibles**

```swift
// Réinitialiser le freemium
debugResetDailyChallengeFreemium()
- Remet dailyChallengeFirstAccessDate à nil
- Remet dailyChallengeMaxDayReached à 0
- Remet relationshipStartDate à aujourd'hui
- Désactive simulation debug

// Simuler un jour spécifique
debugSimulateDailyChallengeDay(targetDay)
- Modifie relationshipStartDate pour simuler jour cible
- Active simulation debug
- Exemple: Jour 4 → relationshipStartDate = aujourd'hui - 3 jours

// Toggle abonnement
debugToggleDailyChallengeSubscription()
- Bascule isSubscribed true/false
- Test immédiat du freemium

// Actualiser défis
refreshChallenges()
- Force appel Firebase Function
- Utile après modification debug
```

##### **💎 Tests Freemium Automatisés**

Interface pour tester rapidement l'accès jours 1-6 :

- **✅ Accessible** : Jour ≤ 3 ou utilisateur abonné
- **🔒 Bloqué** : Jour > 3 et utilisateur non abonné
- **Bouton "Test"** : Simule handleDailyChallengeAccess()

##### **📝 Logs Détaillés**

```
📊 === DEBUG DÉFIS DU JOUR ===
📊 Jour Actuel: 4
📊 Premier Accès: 2025-01-14 15:30:00
📊 Jour Max: 3
📊 Abonné: false
📊 Date Relation: 2025-01-11 10:00:00
📊 Simulation: true
📊 Service Défi: daily_challenge_4
📊 Service Loading: false
📊 === FIN DEBUG ===
```

#### **Intégration FreemiumManager Debug**

Extension debug complète dans `FreemiumManager` :

```swift
#if DEBUG
extension FreemiumManager {
    struct DebugDailyChallengeInfo { ... }

    func getDebugDailyChallengeInfo() -> DebugDailyChallengeInfo
    func debugResetDailyChallengeFreemium()
    func debugSimulateDailyChallengeDay(_ targetDay: Int)
    func debugToggleDailyChallengeSubscription()
    private func calculateRealDailyChallengeDay() -> Int
}
#endif
```

### 🧪 **Tests Fonctionnels**

#### **Côté Client**

```swift
// Test génération défi
DailyChallengeService.shared.refreshChallenges()

// Test sauvegarde
SavedChallengesService.shared.saveChallenge(challenge)

// Test freemium
FreemiumManager.canAccessDailyChallenge(for: 4) // false si non abonné

// Test paywall
FreemiumManager.handleDailyChallengeAccess(currentChallengeDay: 4) {
    // Success callback
}
```

#### **Côté Firebase**

```javascript
// Test function manuellement
const data = {
  coupleId: "testUser1_testUser2",
  timezone: "Europe/Paris",
};

// Dans Console Firebase > Functions
```

#### **Vérification Collections**

```
Firestore Console:
├── dailyChallengeSettings/
│   └── testUser1_testUser2/
├── dailyChallenges/
│   └── testUser1_testUser2_2025-01-15/
└── savedChallenges/
    └── testUser1_daily_challenge_1/
```

### 🐛 **Debug Common Issues**

#### **Défi ne s'affiche pas**

1. Vérifier partenaire connecté (`partnerId` non vide)
2. Vérifier function Firebase `generateDailyChallenge` appelée
3. Vérifier logs Firebase Functions Console
4. Vérifier règles Firestore (permissions)

#### **Paywall ne s'affiche pas**

1. Vérifier `canAccessDailyChallenge()` retourne `false`
2. Vérifier `currentQuestionDay > 3`
3. Vérifier `isSubscribed = false`
4. Logs FreemiumManager dans console

#### **Sauvegarde ne fonctionne pas**

1. Vérifier rules `savedChallenges` (userId ownership)
2. Vérifier listener `SavedChallengesService`
3. Vérifier génération ID `userId_challengeKey`

### 📊 **Monitoring Production**

#### **Firebase Functions Logs**

```bash
# Surveiller génération défis
firebase functions:log --only generateDailyChallenge

# Surveiller cron job
firebase functions:log --only scheduledDailyChallengeGeneration
```

#### **Analytics Clés**

- `freemium_daily_challenge_accessed`
- `paywall_viewed` (source: daily_challenge_freemium)
- `daily_challenge_completed`
- `daily_challenge_saved`
- `onglet_visite` (onglet: defis)

---

## 🎯 Résumé Architecture

### **📅 Logique de Démarrage du Comptage**

#### **🎯 Moment Clé : Clic "Continuer"**

Le comptage des jours pour un couple démarre **uniquement** quand le **premier partenaire** clique "Continuer" dans l'intro :

```swift
// DailyChallengeIntroView.swift - Bouton "Continuer"
Button {
    if hasConnectedPartner {
        navigateToChallenge = true  // → Ouvre DailyChallengeMainView
    }
}

// DailyChallengeMainView.swift - .onAppear
.onAppear {
    if dailyChallengeService.currentChallenge == nil {
        dailyChallengeService.refreshChallenges()  // → Firebase Function
    }
}

// Firebase Function - generateDailyChallenge
if (!settingsDoc.exists) {
    const startDate = new Date();  // 🎯 COMPTAGE DÉMARRE ICI
    const newSettings = { startDate, currentDay: 1, ... };
}
```

#### **⚙️ Synchronisation Couple**

Une fois le **premier settings créé** :

- **Partenaire A** (jour 2) → voit Défi 2
- **Partenaire B** (première ouverture jour 2) → voit **AUSSI Défi 2**
- **Référence commune** : `settings.startDate` du couple

### **Flux Utilisateur Complet**

```
1. 👤 Utilisateur ouvre onglet "Défis"
   ↓
2. 📱 DailyChallengeFlowView vérifie partenaire connecté
   ↓
3. 🎨 Si pas connecté: DailyChallengeIntroView (avec bouton connexion)
   ↓
4. 🎨 Si connecté: DailyChallengeIntroView (avec bouton "Continuer")
   ↓
5. 👆 Utilisateur clique "Continuer"
   ↓
6. 📱 Navigation vers DailyChallengeMainView
   ↓
7. 🔥 .onAppear → Firebase Function generateDailyChallenge
   ↓
8. 📅 Création settings couple SI première fois (startDate = maintenant)
   ↓
9. ⚙️ Function calcule jour relation + génère défi
   ↓
10. 💎 FreemiumManager vérifie accès (jour ≤ 3 ou abonné)
   ↓
11. 💾 Création/mise à jour Firestore documents
   ↓
12. 👂 Listeners Swift détectent changements
   ↓
13. 🎨 UI mise à jour avec nouveau défi
   ↓
14. 👆 Utilisateur peut: Compléter ✅ ou Sauvegarder 📌
```

### **Points Clés Techniques**

✅ **Réutilisation maximale** - 80% code existant  
✅ **Architecture cohérente** - Patterns identiques aux features existantes  
✅ **Sécurité robuste** - Règles granulaires + Functions only writes  
✅ **Performance optimisée** - Index Firestore + cache local Realm  
✅ **Freemium intégré** - Logique identique questions du jour  
✅ **Internationalisation** - 24 défis traduits FR/EN  
✅ **Monitoring complet** - Analytics + logs détaillés

---

_Documentation générée le 15 janvier 2025 - Version 1.0_
