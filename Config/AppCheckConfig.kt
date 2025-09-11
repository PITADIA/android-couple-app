package com.love2loveapp.config

import android.content.Context
import android.util.Log
import com.love2loveapp.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Configuration App Check (équivalent Swift AppCheckConfig)
 * Évite d’exposer les tokens directement dans le code.
 */
object AppCheckConfig {

    /**
     * Debug Token pour Firebase App Check - ACCÈS CENTRALISÉ
     * - Option 1: récupéré depuis une variable d'environnement (RECOMMANDÉ pour CI/CD)
     * - Option 2: récupéré depuis BuildConfig (injecté depuis gradle.properties)
     * - Option 3: récupéré depuis un fichier local `assets/firebase-debug-token.txt` (non versionné)
     * - Option 4: aucun fallback hardcodé
     */
    val debugToken: String? by lazy {
        if (BuildConfig.DEBUG) {
            val token = resolveDebugToken()
            if (token != null) {
                Log.d("AppCheckConfig", "🔑 Debug token résolu depuis: ${getTokenSource(token)}")
            } else {
                Log.w("AppCheckConfig", "⚠️ Aucun debug token trouvé dans toutes les sources")
            }
            token
        } else {
            Log.d("AppCheckConfig", "🏭 Mode RELEASE - pas de debug token")
            null
        }
    }

    /**
     * 🎯 CENTRALISATION: Résout le token depuis toutes les sources possibles
     */
    private fun resolveDebugToken(): String? {
        // Option 1: variable d'environnement (priorité max pour CI/CD)
        val envToken = System.getenv("FIREBASE_DEBUG_TOKEN")
        if (!envToken.isNullOrBlank()) {
            return envToken.trim()
        }

        // Option 2: BuildConfig (depuis gradle.properties)
        if (BuildConfig.FIREBASE_DEBUG_TOKEN.isNotBlank()) {
            return BuildConfig.FIREBASE_DEBUG_TOKEN.trim()
        }

        // Option 3: fichier local dans assets (non versionné)
        return loadTokenFromLocalFile(appContext)
    }

    /**
     * 🔍 Identifie la source du token pour les logs
     */
    private fun getTokenSource(token: String): String {
        return when {
            System.getenv("FIREBASE_DEBUG_TOKEN") == token -> "variable d'environnement (CI/CD)"
            BuildConfig.FIREBASE_DEBUG_TOKEN == token -> "gradle.properties (BuildConfig)"
            loadTokenFromLocalFile(appContext) == token -> "fichier assets local"
            else -> "source inconnue"
        }
    }

    /**
     * Vérifie si un token debug est configuré
     */
    val isDebugTokenConfigured: Boolean
        get() = !debugToken.isNullOrBlank()

    /**
     * Charge le token depuis un fichier assets (non versionné)
     * @param context Contexte application
     */
    private fun loadTokenFromLocalFile(context: Context?): String? {
        if (context == null) return null
        return try {
            val inputStream = context.assets.open("firebase-debug-token.txt")
            val content = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            content.trim()
        } catch (e: Exception) {
            Log.w("AppCheckConfig", "⚠️ Erreur lecture fichier debug token: ${e.message}")
            null
        }
    }

    /**
     * Hack: stocker le contexte application pour lire les assets.
     * Tu peux initialiser `AppCheckConfig.appContext = applicationContext` dans ton AppDelegate.kt
     */
    var appContext: Context? = null
}
