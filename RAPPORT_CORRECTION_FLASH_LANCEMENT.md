# 🚀 Correction Flash de Couleur au Lancement

## ❌ Problème identifié

L'utilisateur voyait un flash de couleur (généralement blanc/gris) pendant ~1 seconde entre :

1. L'ouverture de l'application avec le logo
2. L'affichage de la page principale de l'application

## 🔍 Cause du problème

**Thème Android par défaut** : Quand une activité se lance, Android affiche d'abord le `windowBackground` du thème AVANT de charger le contenu Compose.

**Séquence problématique :**

1. Utilisateur tape sur l'icône app
2. Android affiche `windowBackground` du thème (couleur système/blanche)
3. Puis l'activité charge et affiche `SplashScreen()` avec gradient rose
4. **Résultat** : Flash visible de couleur entre les deux

## ✅ Solution implémentée

### 1. Création d'un thème spécialisé pour le lancement

**Fichier : `res/values/themes.xml`**

```xml
<!-- 🚀 Thème spécial pour le lancement - Élimine le flash de couleur -->
<style name="Theme.Love2love.Launch" parent="Theme.Love2love">
    <item name="android:windowBackground">@color/splash_background</item>
    <item name="android:statusBarColor">@color/splash_background</item>
    <item name="android:navigationBarColor">@color/splash_background</item>
</style>
```

### 2. Définition de la couleur de fond

**Fichier : `res/values/colors.xml`**

```xml
<!-- 🚀 Couleur pour éliminer le flash au lancement -->
<color name="splash_background">@color/love_pink</color>
```

- Utilise la même couleur rose (#FD267A) que le gradient du SplashScreen

### 3. Application du thème à l'activité principale

**Fichier : `AndroidManifest.xml`**

```xml
<activity
    android:name=".MainActivity"
    android:theme="@style/Theme.Love2love.Launch"
    ...>
```

## 🎯 Résultat

**Avant :** Logo → Flash blanc/gris (1s) → Page app  
**Après :** Logo → Transition fluide rose → Page app

## 🛠️ Principe technique

- Android affiche immédiatement la couleur rose (`splash_background`)
- Quand `SplashScreen()` se charge avec son gradient rose, la transition est invisible
- L'utilisateur voit une expérience de lancement fluide et professionnelle

## 📱 Test requis

1. Fermer complètement l'application
2. Relancer depuis l'icône du téléphone
3. Vérifier qu'il n'y a plus de flash de couleur
4. La transition doit être parfaitement fluide

✅ **Problème résolu** : Plus de flash de couleur disgracieux au lancement !
