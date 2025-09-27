package com.love2loveapp.views.profile.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.*

/**
 * 🔗 ProfileExternalLinks - Fonctions utilitaires pour liens externes profil
 * 
 * Équivalent Android des fonctions iOS :
 * - openSupportEmail() → email support Love2Love
 * - openSubscriptionSettings() → Google Play Store abonnements
 * - openTermsAndConditions() → CGV Google Play
 * - openPrivacyPolicy() → Politique confidentialité (multilingue)
 */
object ProfileExternalLinks {
    
    private const val TAG = "ProfileExternalLinks"
    
    // URLs et paramètres
    private const val SUPPORT_EMAIL = "contact@love2loveapp.com"
    private const val SUPPORT_SUBJECT = "Support Love2Love - Question"
    private const val GOOGLE_PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
    private const val GOOGLE_TERMS_URL = "https://play.google.com/about/play-terms/index.html"
    private const val PRIVACY_POLICY_FR = "https://love2lovesite.onrender.com"
    private const val PRIVACY_POLICY_EN = "https://love2lovesite.onrender.com/privacy-policy.html"
    
    /**
     * 📧 Ouvrir l'email de support Love2Love
     * 
     * Équivalent iOS openSupportEmail() avec :
     * - Intent EMAIL avec EXTRA_SUBJECT seulement (multilingue)
     * - Fallback gracieux si pas d'app email
     * - Corps d'email vide pour laisser l'utilisateur écrire
     * - Méthodes multiples pour résoudre les blocages Android
     */
    fun openSupportEmail(context: Context) {
        try {
            Log.d(TAG, "📧 Ouverture email support")
            
            // ✅ MÉTHODE 1: Intent ACTION_SENDTO avec données mailto (le plus robuste)
            val mailtoIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode(SUPPORT_SUBJECT)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (tryStartActivity(context, mailtoIntent, "ACTION_SENDTO avec mailto")) return
            
            // ✅ MÉTHODE 2: Intent ACTION_SENDTO simple
            val sendtoIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$SUPPORT_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, SUPPORT_SUBJECT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (tryStartActivity(context, sendtoIntent, "ACTION_SENDTO simple")) return
            
            // ✅ MÉTHODE 3: Intent ACTION_SEND avec chooser
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, SUPPORT_SUBJECT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val chooserIntent = Intent.createChooser(sendIntent, "Choisir une application email")
            if (tryStartActivity(context, chooserIntent, "ACTION_SEND avec chooser")) return
            
            // ✅ MÉTHODE 4: Intent ACTION_VIEW avec mailto
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode(SUPPORT_SUBJECT)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (tryStartActivity(context, viewIntent, "ACTION_VIEW avec mailto")) return
            
            // ✅ MÉTHODE 5: Apps spécifiques (Gmail, Outlook, etc.)
            if (tryOpenSpecificEmailApp(context)) return
            
            Log.w(TAG, "⚠️ Aucune méthode email n'a fonctionné")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture email support: ${e.message}", e)
        }
    }
    
    /**
     * Essaie de démarrer une activité avec logging
     */
    private fun tryStartActivity(context: Context, intent: Intent, method: String): Boolean {
        return try {
            Log.d(TAG, "🔄 Tentative: $method")
            
            // Vérifier si l'intent peut être résolu avant de l'envoyer
            val packageManager = context.packageManager
            val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, 0)
            }
            
            if (resolveInfo != null) {
                context.startActivity(intent)
                Log.d(TAG, "✅ Email support ouvert avec: $method")
                true
            } else {
                Log.d(TAG, "❌ Aucune app trouvée pour: $method")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "❌ Erreur avec $method: ${e.message}")
            false
        }
    }
    
    /**
     * Essaie d'ouvrir des applications email spécifiques
     */
    private fun tryOpenSpecificEmailApp(context: Context): Boolean {
        val emailApps = listOf(
            "com.google.android.gm", // Gmail
            "com.microsoft.office.outlook", // Outlook
            "com.yahoo.mobile.client.android.mail", // Yahoo Mail
            "com.samsung.android.email.provider", // Samsung Email
            "com.android.email" // Email Android par défaut
        )
        
        for (packageName in emailApps) {
            try {
                Log.d(TAG, "🔄 Tentative app spécifique: $packageName")
                
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, SUPPORT_SUBJECT)
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "✅ Email ouvert avec: $packageName")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "❌ Erreur avec $packageName: ${e.message}")
            }
        }
        
        return false
    }
    
    /**
     * 🌐 Essaie d'ouvrir une URL avec plusieurs méthodes navigateur
     */
    private fun tryOpenUrl(context: Context, url: String, description: String): Boolean {
        Log.d(TAG, "🔄 Tentative ouverture: $description")
        
        // ✅ MÉTHODE 1: Intent ACTION_VIEW direct
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val packageManager = context.packageManager
            val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, 0)
            }
            
            if (resolveInfo != null) {
                context.startActivity(intent)
                Log.d(TAG, "✅ $description ouverte avec ACTION_VIEW")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "❌ Erreur ACTION_VIEW: ${e.message}")
        }
        
        // ✅ MÉTHODE 2: Intent avec chooser
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val chooserIntent = Intent.createChooser(intent, "Choisir un navigateur").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(chooserIntent)
            Log.d(TAG, "✅ $description ouverte avec chooser")
            return true
        } catch (e: Exception) {
            Log.d(TAG, "❌ Erreur chooser: ${e.message}")
        }
        
        // ✅ MÉTHODE 3: Navigateurs spécifiques
        val browsers = listOf(
            "com.android.chrome", // Chrome
            "com.microsoft.emmx", // Edge
            "org.mozilla.firefox", // Firefox
            "com.opera.browser", // Opera
            "com.brave.browser", // Brave
            "com.android.browser" // Navigateur Android par défaut
        )
        
        for (packageName in browsers) {
            try {
                Log.d(TAG, "🔄 Tentative navigateur spécifique: $packageName")
                
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "✅ $description ouverte avec: $packageName")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "❌ Erreur avec $packageName: ${e.message}")
            }
        }
        
        // ✅ MÉTHODE 4: Copier URL dans le presse-papiers (fallback ultime)
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", url)
            clipboard.setPrimaryClip(clip)
            
            Log.d(TAG, "📋 URL copiée dans le presse-papiers: $url")
            // Optionnel: Afficher un toast pour informer l'utilisateur
            return false // Pas vraiment ouvert, mais action alternative
        } catch (e: Exception) {
            Log.d(TAG, "❌ Erreur copie presse-papiers: ${e.message}")
        }
        
        Log.d(TAG, "❌ Toutes les méthodes ont échoué pour: $description")
        return false
    }
    
    /**
     * 💳 Ouvrir les paramètres d'abonnement Google Play Store
     * 
     * Équivalent iOS openSubscriptionSettings() avec :
     * - Tentative ouverture app Google Play Store directe
     * - Fallback vers URL web
     * - Gestion erreurs gracieuse
     */
    fun openSubscriptionSettings(context: Context) {
        try {
            Log.d(TAG, "💳 Ouverture paramètres abonnements Google Play")
            
            // Tentative 1: Ouvrir directement dans l'app Google Play Store
            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
                setPackage("com.android.vending") // Package Google Play Store
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (playStoreIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(playStoreIntent)
                Log.d(TAG, "✅ Abonnements ouverts dans Google Play Store")
                return
            }
            
            // Tentative 2: URL générale abonnements sans package spécifique
            val generalPlayIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(GOOGLE_PLAY_SUBSCRIPTIONS_URL)
                setPackage("com.android.vending")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (generalPlayIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(generalPlayIntent)
                Log.d(TAG, "✅ Abonnements ouverts (général) dans Google Play Store")
                return
            }
            
            // Tentative 3: Fallback vers navigateur web
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_SUBSCRIPTIONS_URL))
            if (browserIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(browserIntent)
                Log.d(TAG, "✅ Abonnements ouverts via navigateur")
            } else {
                Log.w(TAG, "⚠️ Impossible d'ouvrir les abonnements")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture abonnements: ${e.message}", e)
        }
    }
    
    /**
     * 📄 Ouvrir les Conditions Générales de Vente (Google Play)
     * 
     * Équivalent iOS avec lien vers CGV Google Play :
     * - URL officielle Google Play Terms
     * - Méthodes multiples pour résoudre les blocages Android
     */
    fun openTermsAndConditions(context: Context) {
        try {
            Log.d(TAG, "📄 Ouverture CGV Google Play")
            
            // ✅ MÉTHODES MULTIPLES pour navigateurs (comme pour email)
            if (tryOpenUrl(context, GOOGLE_TERMS_URL, "CGV Google Play")) return
            
            Log.w(TAG, "⚠️ Aucune méthode n'a fonctionné pour ouvrir CGV")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture CGV: ${e.message}", e)
        }
    }
    
    /**
     * 🔒 Ouvrir la Politique de Confidentialité (multilingue)
     * 
     * Équivalent iOS openPrivacyPolicy() avec :
     * - URL française ou anglaise selon langue système
     * - Détection automatique langue préférée
     * - Méthodes multiples pour résoudre les blocages Android
     */
    fun openPrivacyPolicy(context: Context) {
        try {
            Log.d(TAG, "🔒 Ouverture politique de confidentialité")
            
            // Détection langue comme iOS
            val isFrench = Locale.getDefault().language.startsWith("fr")
            val policyUrl = if (isFrench) {
                PRIVACY_POLICY_FR
            } else {
                PRIVACY_POLICY_EN
            }
            
            Log.d(TAG, "🌍 Langue détectée: ${Locale.getDefault().language}, URL: $policyUrl")
            
            // ✅ MÉTHODES MULTIPLES pour navigateurs (comme pour email)
            if (tryOpenUrl(context, policyUrl, "Politique de confidentialité")) return
            
            Log.w(TAG, "⚠️ Aucune méthode n'a fonctionné pour ouvrir politique")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture politique: ${e.message}", e)
        }
    }
    
    /**
     * 📱 Ouvrir les paramètres Android (fallback général)
     * 
     * Utilitaire pour ouvrir les paramètres système Android
     * si les autres méthodes échouent
     */
    fun openAndroidSettings(context: Context) {
        try {
            Log.d(TAG, "📱 Ouverture paramètres Android")
            
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "✅ Paramètres Android ouverts")
            } else {
                Log.w(TAG, "⚠️ Impossible d'ouvrir paramètres Android")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture paramètres: ${e.message}", e)
        }
    }
    
    /**
     * 🔍 Vérifier si une intention peut être résolue
     * 
     * Utilitaire pour tester si une app peut gérer une intention
     * avant de la lancer
     */
    fun canResolveIntent(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }
    
    /**
     * 📊 Obtenir informations système pour debug/support
     * 
     * Retourne infos utiles pour le support client
     */
    fun getSystemInfo(): Map<String, String> {
        return mapOf(
            "androidVersion" to android.os.Build.VERSION.RELEASE,
            "deviceModel" to android.os.Build.MODEL,
            "deviceManufacturer" to android.os.Build.MANUFACTURER,
            "language" to Locale.getDefault().language,
            "country" to Locale.getDefault().country,
            "buildType" to android.os.Build.TYPE,
            "apiLevel" to android.os.Build.VERSION.SDK_INT.toString()
        )
    }
}
