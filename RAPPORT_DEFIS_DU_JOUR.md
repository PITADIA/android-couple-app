# Rapport : Système Défis du Jour - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système "Défis du Jour" dans l'application iOS CoupleApp, incluant la connexion partenaire obligatoire, le système freemium (3 jours gratuits), l'interface de défis avec completion, l'intégration Firebase, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Système

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTÈME DÉFIS DU JOUR                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  FLUX ONBOARDING ET CONNEXION (IDENTIQUE QUESTIONS)            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │OnboardingView│  │PartnerCodeStep│  │ChallengeIntro│          │
│  │- 8+ étapes   │  │- Connexion req│  │- Présentation│        │
│  │- Freemium    │  │- Validation   │  │- Actions     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE CLIENT iOS                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │DailyChallengeService│ │DCFlowView   │  │DCMainView    │        │
│  │- Generate Ch │  │- Route Logic │  │- Cards UI    │         │
│  │- Completion  │  │- Freemium 3J │  │- Mark Done   │         │
│  │- Cache Realm │  │- Partner Req │  │- Save System │         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE FIREBASE BACKEND                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Cloud Functions │  │   Firestore  │  │ Notifications │         │
│  │- generateDaily │  │- dailyChall. │  │- Reminders   │         │
│  │- challengeKey │  │- settings    │  │- Completion  │         │
│  │- timezone mgmt│  │- completion  │  │- Saved List  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

FLUX COMPLET:
1. Utilisateur ouvre Défis → DailyChallengeFlowView
2. Route Logic: Partner ? Intro ? Premium ? → Route
3. Si OK → Generate today's challenge → Firebase sync
4. Card interface → Mark complete → Save challenges
5. Analytics tracking → Usage statistics
```

---

## 🚀 1. Flux d'Onboarding - Connexion Partenaire Obligatoire

### 1.1 Route Logic Identique Questions

**Localisation :** `Views/DailyChallenge/DailyChallengeFlowView.swift:51-89`

```swift
private var currentRoute: DailyContentRoute {
    return DailyContentRouteCalculator.calculateRoute(
        for: .dailyChallenge,  // Type différent
        hasConnectedPartner: hasConnectedPartner,
        hasSeenIntro: appState.introFlags.dailyChallenge,  // Flag spécifique défis
        shouldShowPaywall: shouldShowPaywall,
        paywallDay: currentChallengeDay,
        serviceHasError: false,
        serviceErrorMessage: nil,
        serviceIsLoading: dailyChallengeService.isLoading
    )
}

private var hasConnectedPartner: Bool {
    guard let user = appState.currentUser,
          let partnerId = user.partnerId else { return false }
    return !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}
```

### 1.2 Configuration Service Conditionnelle

**Localisation :** `Views/DailyChallenge/DailyChallengeFlowView.swift:91-128`

```swift
private func configureServiceIfNeeded() {
    // 🔑 VÉRIFIER PARTENAIRE CONNECTÉ
    guard let currentUser = appState.currentUser,
          let partnerId = currentUser.partnerId,
          !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
        print("⏳ DailyChallengeFlowView: En attente connexion partenaire")
        return
    }

    // 🔑 VÉRIFIER INTRO VUE
    guard appState.introFlags.dailyChallenge else {
        print("⏳ DailyChallengeFlowView: En attente intro utilisateur")
        return
    }

    // 🔑 OPTIMISATION CACHE: Vérifier si défi d'aujourd'hui disponible
    if let currentChallenge = dailyChallengeService.currentChallenge {
        let today = Date()
        let calendar = Calendar.current
        let challengeDate = currentChallenge.scheduledDate

        if calendar.isDate(challengeDate, inSameDayAs: today) {
            print("⚡ Défi d'aujourd'hui déjà disponible - Pas de reconfiguration")
            return
        }
    }

    // 🔑 GÉRER ACCÈS FREEMIUM AVANT CONFIGURATION
    appState.freemiumManager?.handleDailyChallengeAccess(currentChallengeDay: currentChallengeDay) {
        print("🔄 Configuration service pour récupérer défi du jour")
        dailyChallengeService.configure(with: appState)
    }
}
```

---

## 💰 2. Système Freemium - 3 Jours Gratuits (Identique Questions)

### 2.1 FreemiumManager Extension Défis

**Localisation :** `ViewModels/FreemiumManager.swift:466-551`

```swift
extension FreemiumManager {
    // 🔑 CONFIGURATION IDENTIQUE AUX QUESTIONS
    private var freeDailyChallengesDays: Int { 3 } // 3 premiers jours gratuits

    /// Vérifie si l'utilisateur peut accéder au défi du jour actuel
    func canAccessDailyChallenge(for challengeDay: Int) -> Bool {
        // Si l'utilisateur est abonné, accès illimité
        if appState?.currentUser?.isSubscribed ?? false {
            return true
        }

        print("📅 FreemiumManager: Vérification accès défi jour \(challengeDay)")

        // 🔑 LOGIQUE FREEMIUM : Bloquer après le jour 3
        return challengeDay <= freeDailyChallengesDays
    }

    /// Gère l'accès aux défis du jour avec vérification du jour actuel
    func handleDailyChallengeAccess(currentChallengeDay: Int, onSuccess: @escaping () -> Void) {
        let isSubscribed = appState?.currentUser?.isSubscribed ?? false

        if isSubscribed {
            print("📅 FreemiumManager: Utilisateur premium - Accès défis autorisé")
            markDailyChallengeUsage(day: currentChallengeDay)
            onSuccess()
            return
        }

        // 🔑 VÉRIFICATION FREEMIUM
        if currentChallengeDay <= freeDailyChallengesDays {
            print("📅 FreemiumManager: Défi \(currentChallengeDay)/\(freeDailyChallengesDays) - Accès gratuit")
            markDailyChallengeUsage(day: currentChallengeDay)
            onSuccess()
        } else {
            print("📅 FreemiumManager: Défi \(currentChallengeDay) > limite - Paywall")
            showDailyChallengePaywall()
        }
    }

    private func markDailyChallengeUsage(day: Int) {
        // 🔑 ANALYTICS SPÉCIFIQUES AUX DÉFIS
        Analytics.logEvent("freemium_daily_challenge_accessed", parameters: [
            "challenge_day": day,
            "is_subscribed": false
        ])
    }

    private func showDailyChallengePaywall() {
        showingSubscription = true

        Analytics.logEvent("paywall_affiche", parameters: [
            "source": "daily_challenge_freemium"
        ])
    }
}
```

---

## 🔥 3. Firebase Backend - Génération et Synchronisation Défis

### 3.1 Cloud Function generateDailyChallenge()

**Localisation :** `firebase/functions/index.js:5644-5712`

```javascript
async function generateDailyChallengeCore(
  coupleId,
  timezone,
  challengeDay = null
) {
  console.log(`🎯 generateDailyChallengeCore: coupleId=${coupleId}`);

  try {
    // 🔑 RÉCUPÉRER OU CRÉER SETTINGS
    const settings = await getOrCreateDailyChallengeSettings(
      coupleId,
      timezone
    );

    // 🔑 CALCULER LE JOUR SI PAS FOURNI
    const targetDay = challengeDay || calculateCurrentChallengeDay(settings);

    const today = new Date();
    const todayString = today.toISOString().split("T")[0];

    // 🔑 VÉRIFIER SI DÉFI EXISTE DÉJÀ (IDEMPOTENCE)
    const challengeId = `${coupleId}_${todayString}`;
    const existingDoc = await admin
      .firestore()
      .collection("dailyChallenges")
      .doc(challengeId)
      .get();

    if (existingDoc.exists) {
      console.log(`✅ Défi déjà existant pour ${coupleId} jour ${targetDay}`);
      return {
        success: true,
        challenge: existingDoc.data(),
        alreadyExists: true,
      };
    }

    // 🔑 GÉNÉRER NOUVEAU DÉFI
    const challengeKey = generateChallengeKey(targetDay);
    const challengeData = {
      id: challengeId,
      coupleId,
      challengeKey,
      challengeDay: targetDay,
      scheduledDate: admin.firestore.Timestamp.fromDate(today),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      isCompleted: false, // 🔑 ÉTAT INITIAL
    };

    // 🔑 SAUVEGARDER DANS FIRESTORE
    await admin
      .firestore()
      .collection("dailyChallenges")
      .doc(challengeId)
      .set(challengeData);

    console.log(`✅ Défi généré: ${challengeKey} pour couple ${coupleId}`);
    return {
      success: true,
      challenge: challengeData,
      generated: true,
    };
  } catch (error) {
    console.error(`❌ Erreur génération défi pour ${coupleId}:`, error);
    throw error;
  }
}

// 🔑 GÉNÉRATION CLÉ DÉFI
function generateChallengeKey(challengeDay) {
  return `daily_challenge_${challengeDay}`;
}

function getTotalChallengesCount() {
  return 300; // Nombre total de défis disponibles
}
```

### 3.2 Settings Management Défis

**Localisation :** `firebase/functions/index.js:5511-5596`

```javascript
async function getOrCreateDailyChallengeSettings(
  coupleId,
  timezone = "Europe/Paris"
) {
  console.log(`🔧 getOrCreateDailyChallengeSettings: coupleId=${coupleId}`);

  try {
    const settingsRef = admin
      .firestore()
      .collection("dailyChallengeSettings")
      .doc(coupleId);
    const settingsDoc = await settingsRef.get();

    if (settingsDoc.exists) {
      console.log("📋 Settings défis existants trouvés");
      return settingsDoc.data();
    }

    // 🔑 CRÉER NOUVEAUX SETTINGS DÉFIS
    const now = new Date();
    const startDate = new Date(now);
    startDate.setUTCHours(0, 0, 0, 0);

    const newSettings = {
      coupleId,
      startDate: admin.firestore.Timestamp.fromDate(startDate),
      currentDay: 1,
      timezone: timezone,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      isActive: true,
      totalChallengesCompleted: 0, // 🔑 COMPTEUR COMPLETIONS
    };

    await settingsRef.set(newSettings);
    console.log(`✅ Nouveaux settings défis créés pour couple ${coupleId}`);

    return newSettings;
  } catch (error) {
    console.error(`❌ Erreur getOrCreateDailyChallengeSettings:`, error);
    throw error;
  }
}
```

---

## 🎯 4. Interface Défis - Cartes avec Système Completion

### 4.1 DailyChallengeMainView - Interface Principale

**Localisation :** `Views/DailyChallenge/DailyChallengeMainView.swift:1-40`

```swift
struct DailyChallengeMainView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyChallengeService = DailyChallengeService.shared
    @StateObject private var savedChallengesService = SavedChallengesService.shared
    @State private var showingSavedChallenges = false

    var body: some View {
        NavigationView {
            ZStack {
                // 🔑 FOND IDENTIQUE AUX FAVORIS
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // 🔑 HEADER AVEC TITRE ET SOUS-TITRE FREEMIUM
                    HStack {
                        Spacer()
                        VStack(spacing: 4) {
                            Text(NSLocalizedString("daily_challenges_title", tableName: "DailyChallenges", comment: ""))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)

                            // 🔑 SOUS-TITRE FREEMIUM
                            if let subtitle = getDailyChallengeSubtitle() {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.top, 4)
                            }
                        }
                        Spacer()

                        // 🔑 ICÔNE DÉFIS SAUVEGARDÉS
                        Button(action: {
                            showingSavedChallenges = true
                        }) {
                            Image(systemName: "bookmark.circle.fill")
                                .font(.system(size: 28))
                                .foregroundColor(Color(red: 1.0, green: 0.42, blue: 0.62))
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 10)

                    // 🔑 CONTENU PRINCIPAL - CARTES DÉFIS
                    if let challenge = dailyChallengeService.currentChallenge {
                        DailyChallengeCardView(
                            challenge: challenge,
                            onCompleted: {
                                // Handle completion
                            },
                            onSave: {
                                savedChallengesService.saveChallenge(challenge)
                            }
                        )
                        .padding(.horizontal, 20)
                        .padding(.top, 30)
                    } else {
                        // 🔑 ÉTAT CHARGEMENT
                        LoadingView()
                    }

                    Spacer()
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingSavedChallenges) {
            SavedChallengesView()
                .environmentObject(appState)
        }
    }
}
```

### 4.2 DailyChallengeCardView - Carte avec Actions

**Localisation :** `Views/DailyChallenge/DailyChallengeCardView.swift:172-188`

```swift
// MARK: - Actions Completion

private func handleChallengeCompleted() {
    let newCompletionState = !isCompleted

    // 🔑 ANIMATION SPRING
    withAnimation(.spring(response: 0.6)) {
        isCompleted = newCompletionState
    }

    // 🔑 PERSISTER L'ÉTAT DANS FIREBASE ET CACHE
    if newCompletionState {
        DailyChallengeService.shared.markChallengeAsCompleted(challenge)
    } else {
        DailyChallengeService.shared.markChallengeAsNotCompleted(challenge)
    }

    onCompleted?()
}

private func handleChallengeSave() {
    if !isAlreadySaved {
        onSave?()
        showSaveConfirmation = true
        isAlreadySaved = true

        // 🔑 RESET CONFIRMATION APRÈS 3 SECONDES
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            showSaveConfirmation = false
        }
    }
}
```

### 4.3 Challenge Completion Service

**Localisation :** `Services/DailyChallengeService.swift:340-374`

```swift
// MARK: - Challenge Completion

func markChallengeAsCompleted(_ challenge: DailyChallenge) {
    guard let currentUser = appState?.currentUser else { return }

    var updatedChallenge = challenge
    updatedChallenge.isCompleted = true
    updatedChallenge.completedAt = Date()

    // 🔑 METTRE À JOUR LOCALEMENT
    if let index = challengeHistory.firstIndex(where: { $0.id == challenge.id }) {
        challengeHistory[index] = updatedChallenge
    }

    if currentChallenge?.id == challenge.id {
        currentChallenge = updatedChallenge
    }

    // 🔑 METTRE À JOUR CACHE REALM
    QuestionCacheManager.shared.updateDailyChallengeCompletion(
        challenge.id,
        isCompleted: true,
        completedAt: Date()
    )

    // 🔑 METTRE À JOUR FIREBASE
    db.collection("dailyChallenges").document(challenge.id).updateData([
        "isCompleted": true,
        "completedAt": Timestamp(date: Date())
    ]) { error in
        if let error = error {
            print("❌ Erreur mise à jour completion: \(error)")
        } else {
            print("✅ Défi marqué comme complété")
        }
    }
}
```

---

## 🌍 5. Localisation - Clés XCStrings Défis

### 5.1 Structure DailyChallenges.xcstrings

**Localisation :** `DailyChallenges.xcstrings`

```json
{
  "sourceLanguage": "fr",
  "strings": {
    // 🔑 CLÉS PRINCIPALES INTERFACE
    "daily_challenges_title": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": { "state": "translated", "value": "Défi du jour" }
        },
        "en": {
          "stringUnit": { "state": "translated", "value": "Daily Challenge" }
        },
        "de": {
          "stringUnit": { "state": "translated", "value": "Tages‑Challenge" }
        },
        "es": {
          "stringUnit": { "state": "translated", "value": "Desafío diario" }
        },
        "it": {
          "stringUnit": { "state": "translated", "value": "Sfida quotidiana" }
        },
        "pt-BR": {
          "stringUnit": { "state": "translated", "value": "Desafio diário" }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Dagelijkse uitdaging"
          }
        }
      }
    },

    // 🔑 CLÉS PAYWALL SPÉCIFIQUES
    "paywall_page_title_challenges": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": { "state": "translated", "value": "Défis du Jour" }
        },
        "en": {
          "stringUnit": { "state": "translated", "value": "Daily Challenges" }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Tägliche Challenges"
          }
        },
        "es": {
          "stringUnit": { "state": "translated", "value": "Desafíos diarios" }
        },
        "it": {
          "stringUnit": { "state": "translated", "value": "Sfide quotidiane" }
        },
        "pt-BR": {
          "stringUnit": { "state": "translated", "value": "Desafios diários" }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Dagelijkse uitdagingen"
          }
        }
      }
    },

    // 🔑 DÉFIS DYNAMIQUES (daily_challenge_1, daily_challenge_2, etc.)
    "daily_challenge_1": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Envoyez-lui un message pour lui dire pourquoi vous êtes reconnaissant de l'avoir dans votre vie."
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Send them a message telling them why you're grateful to have them in your life."
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Schick deinem Partner/deiner Partnerin eine Nachricht und sag, warum du dankbar bist, dass er/sie in deinem Leben ist."
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Envíale un mensaje diciéndole por qué agradeces tenerle en tu vida."
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Invia al tuo partner un messaggio spiegando perché sei grato di averlo nella tua vita."
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Envie uma mensagem dizendo por que você é grato(a) por tê-lo(a) na sua vida."
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Stuur hem/haar een bericht waarin je vertelt waarom je dankbaar bent dat hij/zij in je leven is."
          }
        }
      }
    },

    "daily_challenge_2": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Préparez ensemble le petit-déjeuner de demain matin."
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Prepare tomorrow morning's breakfast together."
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Bereitet zusammen das Frühstück für morgen früh vor."
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Preparad juntos el desayuno de mañana por la mañana."
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Preparate insieme la colazione di domani mattina."
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Preparem juntos o café da manhã de amanhã."
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Bereid samen het ontbijt van morgenochtend voor."
          }
        }
      }
    },

    "daily_challenge_3": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Planifiez une sortie surprise pour ce week-end."
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Plan a surprise outing for this weekend."
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Plant einen Überraschungsausflug für dieses Wochenende."
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Planificad una salida sorpresa para este fin de semana."
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Pianificate un'uscita a sorpresa per questo weekend."
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Planejem um passeio surpresa para este fim de semana."
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Plan een verrassingsuitje voor dit weekend."
          }
        }
      }
    }

    // ... jusqu'à daily_challenge_300+ selon le contenu disponible
  }
}
```

### 5.2 Usage Dynamique des Clés

**Localisation :** `Models/DailyChallenge.swift:37-39`

```swift
/// Retourne le texte localisé du défi
var localizedText: String {
    return challengeKey.localized(tableName: "DailyChallenges")
}
```

---

## 🤖 6. Adaptation Android - Architecture Kotlin/Compose

### 6.1 Structure Android Strings.xml

```xml
<!-- res/values/daily_challenges_strings.xml -->
<resources>
    <!-- Interface principale -->
    <string name="daily_challenges_title">Défi du jour</string>
    <string name="saved_challenges_title">Défis sauvegardés</string>

    <!-- États et actions -->
    <string name="challenge_completed">Défi complété !</string>
    <string name="challenge_mark_done">Marquer comme fait</string>
    <string name="challenge_save">Sauvegarder</string>
    <string name="challenge_saved">Défi sauvegardé !</string>

    <!-- Paywall -->
    <string name="paywall_page_title_challenges">Défis du Jour</string>

    <!-- Défis dynamiques -->
    <string name="daily_challenge_1">Envoyez-lui un message pour lui dire pourquoi vous êtes reconnaissant de l\'avoir dans votre vie.</string>
    <string name="daily_challenge_2">Préparez ensemble le petit-déjeuner de demain matin.</string>
    <string name="daily_challenge_3">Planifiez une sortie surprise pour ce week-end.</string>

    <!-- Localizations multilingues dans res/values-en/, res/values-de/, etc. -->
</resources>

<!-- res/values-en/daily_challenges_strings.xml -->
<resources>
    <string name="daily_challenges_title">Daily Challenge</string>
    <string name="saved_challenges_title">Saved Challenges</string>
    <string name="challenge_completed">Challenge completed!</string>
    <string name="challenge_mark_done">Mark as done</string>
    <string name="challenge_save">Save</string>
    <string name="challenge_saved">Challenge saved!</string>
    <string name="paywall_page_title_challenges">Daily Challenges</string>
    <string name="daily_challenge_1">Send them a message telling them why you\'re grateful to have them in your life.</string>
    <string name="daily_challenge_2">Prepare tomorrow morning\'s breakfast together.</string>
    <string name="daily_challenge_3">Plan a surprise outing for this weekend.</string>
</resources>
```

### 6.2 DailyChallengeRepository Android

```kotlin
@Singleton
class DailyChallengeRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService
) {

    companion object {
        private const val TAG = "DailyChallengeRepository"
        private const val COLLECTION_DAILY_CHALLENGES = "dailyChallenges"
        private const val COLLECTION_SETTINGS = "dailyChallengeSettings"
    }

    private val _currentChallenge = MutableStateFlow<DailyChallenge?>(null)
    val currentChallenge: StateFlow<DailyChallenge?> = _currentChallenge

    private val _challengeHistory = MutableStateFlow<List<DailyChallenge>>(emptyList())
    val challengeHistory: StateFlow<List<DailyChallenge>> = _challengeHistory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var challengeListener: ListenerRegistration? = null

    // MARK: - Configuration et Setup

    fun initializeForCouple(coupleId: String) {
        Log.d(TAG, "Initialisation défis pour couple: $coupleId")
        setupRealtimeListener(coupleId)
        generateTodayChallenge(coupleId)
    }

    // MARK: - Real-time Listener

    private fun setupRealtimeListener(coupleId: String) {
        challengeListener?.remove()

        Log.d(TAG, "Configuration listener temps réel défis pour couple: $coupleId")

        // 🔑 LISTENER FIREBASE TEMPS RÉEL
        challengeListener = firestore.collection(COLLECTION_DAILY_CHALLENGES)
            .whereEqualTo("coupleId", coupleId)
            .orderBy("challengeDay", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur listener défis: ${error.message}")
                    return@addSnapshotListener
                }

                Log.d(TAG, "Mise à jour défis reçue: ${snapshot?.documents?.size} documents")

                val challenges = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        DailyChallenge.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing défi: ${e.message}")
                        null
                    }
                } ?: emptyList()

                // Trouver le défi d'aujourd'hui
                val today = Date()
                val calendar = Calendar.getInstance()
                val todayChallenge = challenges.find { challenge ->
                    calendar.isDate(challenge.scheduledDate, today)
                }

                _currentChallenge.value = todayChallenge
                _challengeHistory.value = challenges

                Log.d(TAG, "Défi d'aujourd'hui mis à jour: ${todayChallenge?.challengeKey}")
            }
    }

    // MARK: - Génération de Défi

    private fun generateTodayChallenge(coupleId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val currentUser = authRepository.getCurrentUser()
                    ?: throw Exception("Utilisateur non connecté")

                // Calculer le jour actuel
                val challengeDay = calculateCurrentChallengeDay(coupleId)

                // 🔑 APPEL CLOUD FUNCTION
                val data = hashMapOf(
                    "coupleId" to coupleId,
                    "userId" to currentUser.uid,
                    "challengeDay" to challengeDay,
                    "timezone" to TimeZone.getDefault().id
                )

                val result = functions.getHttpsCallable("generateDailyChallenge")
                    .call(data)
                    .await()

                val resultData = result.data as? Map<String, Any>
                val success = resultData?.get("success") as? Boolean ?: false

                if (success) {
                    Log.d(TAG, "Défi généré avec succès pour le jour $challengeDay")

                    analyticsService.logEvent("daily_challenge_generated") {
                        param("challenge_day", challengeDay.toLong())
                        param("couple_id_hash", coupleId.hashCode().toLong())
                    }
                } else {
                    val message = resultData?.get("message") as? String ?: "Erreur inconnue"
                    Log.e(TAG, "Erreur génération défi: $message")
                }

                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(TAG, "Erreur génération défi: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    // MARK: - Challenge Completion

    suspend fun markChallengeAsCompleted(challengeId: String): Result<Unit> {
        return try {
            val currentUser = authRepository.getCurrentUser()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            // 🔑 METTRE À JOUR FIRESTORE
            firestore.collection(COLLECTION_DAILY_CHALLENGES)
                .document(challengeId)
                .update(
                    mapOf(
                        "isCompleted" to true,
                        "completedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            // 📊 Analytics
            analyticsService.logEvent("daily_challenge_completed") {
                param("challenge_id_hash", challengeId.hashCode().toLong())
            }

            Log.d(TAG, "Défi marqué comme complété: $challengeId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur completion défi: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markChallengeAsNotCompleted(challengeId: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_DAILY_CHALLENGES)
                .document(challengeId)
                .update(
                    mapOf(
                        "isCompleted" to false,
                        "completedAt" to null
                    )
                )
                .await()

            Log.d(TAG, "Défi marqué comme non complété: $challengeId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur unmark completion: ${e.message}")
            Result.failure(e)
        }
    }

    fun cleanup() {
        challengeListener?.remove()
    }
}
```

### 6.3 Modèles de Données Android

```kotlin
// DailyChallenge.kt
data class DailyChallenge(
    val id: String,
    val challengeKey: String,
    val challengeDay: Int,
    val scheduledDate: Date,
    val coupleId: String,
    val isCompleted: Boolean = false,
    val completedAt: Date? = null,
    val createdAt: com.google.firebase.Timestamp? = null
) {

    companion object {
        fun fromFirestore(document: DocumentSnapshot): DailyChallenge? {
            return try {
                val data = document.data ?: return null

                DailyChallenge(
                    id = document.id,
                    challengeKey = data["challengeKey"] as? String ?: "",
                    challengeDay = (data["challengeDay"] as? Number)?.toInt() ?: 0,
                    scheduledDate = (data["scheduledDate"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                    coupleId = data["coupleId"] as? String ?: "",
                    isCompleted = data["isCompleted"] as? Boolean ?: false,
                    completedAt = (data["completedAt"] as? com.google.firebase.Timestamp)?.toDate(),
                    createdAt = data["createdAt"] as? com.google.firebase.Timestamp
                )
            } catch (e: Exception) {
                Log.e("DailyChallenge", "Erreur parsing Firestore: ${e.message}")
                null
            }
        }
    }

    // 🔑 LOCALISATION DYNAMIQUE
    fun getLocalizedText(context: Context): String {
        val resourceId = context.resources.getIdentifier(
            challengeKey,
            "string",
            context.packageName
        )

        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            challengeKey.replace("_", " ").capitalize()
        }
    }
}
```

### 6.4 Interface Android - DailyChallengeScreen Compose

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeScreen(
    viewModel: DailyChallengeViewModel = hiltViewModel(),
    onNavigateToPartnerConnection: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToSavedChallenges: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    // 🔑 GESTION DES ROUTES (IDENTIQUE QUESTIONS)
    when (uiState.currentRoute) {
        is DailyContentRoute.Intro -> {
            DailyChallengeIntroScreen(
                showConnectButton = uiState.currentRoute.showConnect,
                onConnectPartner = onNavigateToPartnerConnection,
                onContinue = { viewModel.markIntroSeen() }
            )
        }

        is DailyContentRoute.Paywall -> {
            DailyChallengePaywallScreen(
                challengeDay = uiState.currentRoute.day,
                onSubscribe = onNavigateToSubscription,
                onDismiss = { /* Handle paywall dismiss */ }
            )
        }

        is DailyContentRoute.Main -> {
            DailyChallengeMainScreen(
                challenge = uiState.currentChallenge,
                isLoading = uiState.isLoading,
                onToggleCompletion = viewModel::toggleChallengeCompletion,
                onSaveChallenge = viewModel::saveChallenge,
                onNavigateToSavedChallenges = onNavigateToSavedChallenges
            )
        }

        is DailyContentRoute.Error -> {
            DailyChallengeErrorScreen(
                message = uiState.currentRoute.message,
                onRetry = { viewModel.retry() }
            )
        }

        DailyContentRoute.Loading -> {
            DailyChallengeLoadingScreen()
        }
    }
}

@Composable
fun DailyChallengeMainScreen(
    challenge: DailyChallenge?,
    isLoading: Boolean,
    onToggleCompletion: (String) -> Unit,
    onSaveChallenge: (DailyChallenge) -> Unit,
    onNavigateToSavedChallenges: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        // 🔑 HEADER AVEC TITRE ET BOUTON SAUVEGARDÉS
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.daily_challenges_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onNavigateToSavedChallenges) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.saved_challenges_title),
                        tint = Color(0xFFFF6B9D),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFF7F7F8)
            )
        )

        if (challenge != null) {
            // 🔑 CARTE DÉFI PRINCIPAL
            DailyChallengeCard(
                challenge = challenge,
                context = context,
                onToggleCompletion = { onToggleCompletion(challenge.id) },
                onSaveChallenge = { onSaveChallenge(challenge) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        } else {
            // 🔑 ÉTAT CHARGEMENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF6B9D),
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = "Génération du défi du jour...",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun DailyChallengeCard(
    challenge: DailyChallenge,
    context: Context,
    onToggleCompletion: () -> Unit,
    onSaveChallenge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFE63C6B)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // En-tête jour
                Text(
                    text = "Jour ${challenge.challengeDay}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )

                // 🔑 TEXTE DÉFI LOCALISÉ
                Text(
                    text = challenge.getLocalizedText(context),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )

                // Date
                Text(
                    text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(challenge.scheduledDate),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                // 🔑 ACTIONS COMPLETION ET SAUVEGARDE
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton completion
                    Button(
                        onClick = onToggleCompletion,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (challenge.isCompleted)
                                Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (challenge.isCompleted)
                                Color(0xFFFF6B9D) else Color.White
                        ),
                        shape = RoundedCornerShape(25.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (challenge.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = if (challenge.isCompleted)
                                    stringResource(R.string.challenge_completed)
                                else
                                    stringResource(R.string.challenge_mark_done),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bouton sauvegarde
                    IconButton(
                        onClick = {
                            onSaveChallenge()
                            showSaveConfirmation = true
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Filled.BookmarkAdd,
                            contentDescription = stringResource(R.string.challenge_save),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 🔑 CONFIRMATION SAUVEGARDE
                if (showSaveConfirmation) {
                    LaunchedEffect(showSaveConfirmation) {
                        delay(3000)
                        showSaveConfirmation = false
                    }

                    Text(
                        text = stringResource(R.string.challenge_saved),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
```

---

## 📋 Conclusion

Le système Défis du Jour présente une architecture similaire aux Questions du Jour avec des spécificités importantes :

### 🎯 **Points Forts Spécifiques aux Défis :**

- **Actions concrètes** : Défis orientés action plutôt que conversation
- **Système completion** : Marquer comme "fait" avec persistance Firebase + cache
- **Défis sauvegardés** : System de bookmarks pour retrouver défis préférés
- **Interface cartes** : Design premium avec gradients et animations spring
- **Analytics spécifiques** : Tracking completion, usage freemium défis

### 🔧 **Composants Techniques iOS :**

- `DailyChallengeService` - Service central avec cache Realm optimisé
- `DailyChallengeFlowView` - Router avec même logique que questions
- `DailyChallengeCardView` - Interface cartes avec actions completion/save
- `SavedChallengesService` - Gestion défis sauvegardés localement
- Cloud Functions - `generateDailyChallenge()` avec nettoyage automatique

### 🔥 **Firebase Integration Défis :**

- **Collections** : `dailyChallenges`, `dailyChallengeSettings`
- **Completion tracking** : `isCompleted`, `completedAt` avec analytics
- **Cache strategy** : Realm first → Firebase fallback (performance)
- **Real-time listeners** : Synchronisation instantanée états completion

### 🌍 **Localisation 300+ Défis :**

- **8 langues** : FR, EN, DE, ES, IT, PT-BR, PT-PT, NL
- **Défis dynamiques** : `daily_challenge_1` → `daily_challenge_300+`
- **Actions localisées** : Tous boutons et états traduits
- **Adaptation Android** : Conversion XCStrings → strings.xml

### 🤖 **Architecture Android Robuste :**

- **Repository Pattern** : `DailyChallengeRepository` avec completion logic
- **Compose UI** : Cartes Material Design 3 avec animations
- **StateFlow reactive** : États completion temps réel
- **Freemium identique** : 3 jours gratuits avec même logique

### ⚡ **Différences Clés vs Questions :**

- **Pas de chat** : Interface cartes statiques vs messagerie
- **Système completion** : États binaires (fait/pas fait) vs conversations
- **Défis sauvegardés** : Bookmarking vs favoris questions
- **Actions physiques** : Encouragent activités couple vs échanges verbaux
- **Analytics completion** : Métriques engagement différentes

### 📊 **Métriques Business Défis :**

- **Taux completion** : % défis marqués comme faits
- **Défis sauvegardés** : Top défis préférés couples
- **Conversion freemium** : Jour 4+ → Premium (identique questions)
- **Engagement quotidien** : Habitude action vs habitude conversation

### ⏱️ **Estimation Android : 10-15 semaines**

- Phase 1 : Repository + Models (2-3 sem)
- Phase 2 : UI Cartes Compose (3-4 sem)
- Phase 3 : Completion Logic (2-3 sem)
- Phase 4 : Saved Challenges (2-3 sem)
- Phase 5 : Tests + Polish (1-2 sem)

**Total : 10-15 semaines** (plus rapide que Questions car pas de chat complexe).

Le système Défis du Jour complète parfaitement les Questions du Jour en proposant des **actions concrètes** vs **conversations**, créant un **écosystème complet d'engagement couple** avec mécaniques complémentaires. 🚀

L'architecture partagée avec Questions optimise le développement Android, permettant de **réutiliser 70% du code commun** (routing, freemium, Firebase, auth) tout en spécialisant les **30% spécifiques** (completion, cartes, sauvegarde).
