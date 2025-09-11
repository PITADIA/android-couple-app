# 📱 Love2Love Widgets Android

## 🎯 Vue d'ensemble

Version Android complète des widgets Love2Love iOS, recodée en Kotlin avec fonctionnalités équivalentes :

- ✅ **Widget Principal** : Compteur de jours + photos de profil (Small & Medium)
- ✅ **Widget Distance** : Distance entre les partenaires (Small uniquement)
- ✅ **Gestion Premium** : Widgets bloqués sans abonnement
- ✅ **Localisation** : Support complet des coordonnées GPS
- ✅ **Deep Links** : Integration avec `coupleapp://subscription`

## 📁 Structure du Code

```
ANDROID/Love2LoveWidget/
├── Love2LoveWidgetProvider.kt           # Widget principal (Small + Medium)
├── Love2LoveDistanceWidgetProvider.kt   # Widget de distance
├── WidgetData.kt                        # Modèle de données + SharedPreferences
├── WidgetManager.kt                     # API simple pour l'app principale
└── README.md                           # Cette documentation

ANDROID/app/src/main/res/
├── layout/
│   ├── widget_small_love2love.xml      # Layout widget Small principal
│   ├── widget_medium_love2love.xml     # Layout widget Medium principal
│   ├── widget_small_distance.xml       # Layout widget Distance
│   ├── widget_premium_blocked.xml      # Layout premium bloqué (Medium)
│   └── widget_premium_blocked_small.xml # Layout premium bloqué (Small)
├── drawable/
│   ├── widget_background.xml           # Arrière-plan noir flou
│   ├── widget_profile_circle.xml       # Cercles photos profil
│   ├── widget_preview_main.xml         # Preview widget principal
│   └── widget_preview_distance.xml     # Preview widget distance
├── xml/
│   ├── love2love_widget_info.xml       # Config widget principal
│   └── love2love_distance_widget_info.xml # Config widget distance
└── values/
    ├── strings.xml                     # Strings des widgets
    └── colors.xml                      # Couleurs des widgets
```

## 🚀 Utilisation dans l'App Principale

### Configuration Initiale

```kotlin
// Dans votre AppState ou ViewModel principal
import com.love2loveapp.widget.WidgetManager

// Mettre à jour toutes les données du widget
WidgetManager.updateWidgetData(
    context = context,
    daysTotal = relationshipDays,
    duration = "2 ans et 3 mois",
    daysToAnniversary = 45,
    distance = "3.2 km",
    userName = currentUser.name,
    partnerName = partner.name,
    userLatitude = userLocation.latitude,
    userLongitude = userLocation.longitude,
    partnerLatitude = partnerLocation.latitude,
    partnerLongitude = partnerLocation.longitude,
    hasSubscription = freemiumManager.hasActiveSubscription
)
```

### Mises à jour Partielles

```kotlin
// Mise à jour du statut d'abonnement seulement
WidgetManager.updateSubscriptionStatus(context, true)

// Mise à jour de la localisation seulement
WidgetManager.updateLocationData(
    context = context,
    userLatitude = newLat,
    userLongitude = newLng,
    distance = "5.1 km"
)

// Vérifier si des widgets sont installés
if (WidgetManager.hasActiveWidgets(context)) {
    // L'utilisateur a des widgets → mettre à jour régulièrement
}

// Forcer une mise à jour complète
WidgetManager.forceUpdateAllWidgets(context)

// Nettoyer lors de la déconnexion
WidgetManager.clearWidgetData(context)
```

## 🎨 Types de Widgets

### 1. Widget Principal Small (2x2)

- **Photos de profil** : Initiales dans des cercles
- **Compteur** : Nombre de jours ensemble
- **Texte** : "ensemble"
- **Clic** : Ouvre l'app principale

### 2. Widget Principal Medium (4x2)

- **Section gauche** : Photos miniatures + compteur comme Small
- **Section droite** : Distance avec icône cœur
- **Premium** : Bloqué sans abonnement
- **Clic** : Ouvre l'app principale

### 3. Widget Distance Small (2x2)

- **Photos de profil** : Initiales dans des cercles
- **Distance** : Affichage de la distance ou message d'erreur
- **Premium** : Bloqué sans abonnement
- **Clic** : Ouvre l'app principale

## 🔒 Gestion Premium

Les widgets Medium et Distance sont **automatiquement bloqués** sans abonnement :

```kotlin
// Dans le provider du widget
if (!widgetData.hasSubscription) {
    return createPremiumBlockedView(context, appWidgetId)
}
```

Le widget bloqué affiche :

- 🔒 Icône de cadenas
- "Abonnement requis"
- "Appuyez pour débloquer"
- **Clic** → `coupleapp://subscription`

## 📍 Gestion de la Localisation

Gestion intelligente des cas d'erreur de localisation :

```kotlin
enum class LocationStatus {
    BOTH_AVAILABLE,      // Affiche la distance
    USER_MISSING,        // "Activez votre localisation"
    PARTNER_MISSING,     // "Partenaire doit activer localisation"
    BOTH_MISSING         // "Activez vos localisations"
}
```

## 🌍 Support Multilingue

Conversion automatique km ↔ miles selon la locale :

```kotlin
// Anglais : "3.2 km" → "2.0 mi"
// Français : "3.2 km" → "3.2 km"
private fun formatDistanceForLocale(distance: String, context: Context): String
```

Cas spéciaux :

- `"ensemble"` → `"Ensemble"` (français)
- `"together"` → `"Together"` (anglais)

## 🔄 Mise à jour Automatique

- **Intervalle** : 30 minutes (configurable)
- **Trigger manuel** : Depuis l'app via `WidgetManager`
- **Sauvegarde** : SharedPreferences (équivalent UserDefaults iOS)

## 📱 Intégration MainActivity

Les widgets déclenchent l'ouverture de l'app avec des extras :

```kotlin
// Dans MainActivity.onCreate() ou onNewIntent()
val fromWidget = intent.getBooleanExtra("from_widget", false)
val widgetType = intent.getStringExtra("widget_type") // "small", "medium", "distance"

if (fromWidget) {
    // Naviguer vers l'écran approprié
    when (widgetType) {
        "distance" -> navigateToLocationScreen()
        else -> navigateToHomeScreen()
    }
}
```

## 🎯 Équivalence iOS ↔ Android

| iOS                            | Android                                  |
| ------------------------------ | ---------------------------------------- |
| `WidgetKit`                    | `AppWidgetProvider`                      |
| `UserDefaults.suiteName`       | `SharedPreferences`                      |
| `@Environment(\.widgetFamily)` | `AppWidgetManager.getAppWidgetOptions()` |
| `TimelineEntry`                | Mise à jour manuelle                     |
| `SwiftUI Views`                | `RemoteViews` + XML layouts              |
| App Groups                     | Stockage interne app                     |

## 🚀 Installation et Configuration

1. **Ajouter les permissions** dans `AndroidManifest.xml` ✅
2. **Déclarer les receivers** dans `AndroidManifest.xml` ✅
3. **Importer les layouts** et ressources ✅
4. **Intégrer `WidgetManager`** dans votre AppState ✅

## 🐛 Debug et Logs

Logs détaillés avec tags spécifiques :

```kotlin
// Widget principal
private const val TAG = "Love2LoveWidget"

// Widget distance
private const val TAG = "Love2LoveDistanceWidget"

// Gestionnaire de données
private const val TAG = "WidgetData"

// Manager principal
private const val TAG = "WidgetManager"
```

## ✨ Fonctionnalités Avancées

- **Anti-doublon** : Gestion intelligente des mises à jour
- **Fallback gracieux** : Données placeholder si pas de données
- **Performance** : Mise à jour asynchrone avec coroutines
- **Sécurité** : Validation des données avant affichage
- **Robustesse** : Gestion d'erreur complète

---

**🎉 Vos widgets Love2Love Android sont maintenant entièrement fonctionnels et équivalents à la version iOS !**
