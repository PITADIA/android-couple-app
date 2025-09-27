# ⏳ Keys Loading - Question du Jour

## 🔑 Clés de Traduction

### Titre Principal

```json
"daily_question_preparing": {
  "fr": "Préparation en cours…",
  "en": "Preparing…",
  "de": "Wird vorbereitet…"
}
```

### Sous-titre

```json
"daily_question_preparing_subtitle": {
  "fr": "Nous préparons votre espace de discussion",
  "en": "We are preparing your chat space",
  "de": "Wir richten euren Chat ein"
}
```

## 💻 Usage iOS

```swift
Text(NSLocalizedString("daily_question_preparing", tableName: "DailyQuestions", comment: ""))
Text(NSLocalizedString("daily_question_preparing_subtitle", tableName: "DailyQuestions", comment: ""))
```

**Fichier source :** `DailyQuestions.xcstrings`

## 📱 Context d'Affichage

- Quand `dailyQuestionService.isLoading` = true
- Quand `dailyQuestionService.currentQuestion` = nil
- Première génération de question via Firebase
- Animation cercle rose Love2Love (#FD267A)

## 🤖 Android Adaptation

```xml
<string name="daily_question_preparing">Préparation en cours…</string>
<string name="daily_question_preparing_subtitle">Nous préparons votre espace de discussion</string>
```

```kotlin
Text(text = stringResource(R.string.daily_question_preparing))
Text(text = stringResource(R.string.daily_question_preparing_subtitle))
```
